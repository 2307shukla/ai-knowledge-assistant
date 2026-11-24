package com.abhijeet.ai_knowledge_assistant.service;

import com.abhijeet.ai_knowledge_assistant.domain.DocumentChunk;
import com.abhijeet.ai_knowledge_assistant.domain.KnowledgeDocument;
import com.abhijeet.ai_knowledge_assistant.rag.SourceHit;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Vector store access. PgVectorStore when PostgreSQL is configured, otherwise a JSON file on disk.
 *
 * <p><b>Privacy:</b> every chunk is stored with an {@code owner} metadata field, and every search
 * goes through {@link #search(String, String, int)}, which filters to the caller's own documents
 * plus the public sample. There is no unfiltered search method.
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    public static final String OWNER = "owner";
    public static final String DOCUMENT_ID = "documentId";
    public static final String FILE = "file";
    public static final String PAGE = "page";
    public static final String POSITION = "position";

    /** Passages retrieved per question. */
    public static final int TOP_K = 4;

    private final VectorStore vectorStore;
    private final SimpleVectorStore fileStore;
    private final Path storePath;

    public RagService(EmbeddingModel embeddingModel,
                      ObjectProvider<VectorStore> providedStore,
                      @Value("${app.vector-store.path:./data/vector-store.json}") String path) {
        VectorStore custom = providedStore.getIfAvailable();
        if (custom != null) {
            this.vectorStore = custom;
            this.fileStore = null;
            this.storePath = null;
            log.info("Vector store backend: PgVectorStore (persistent)");
        } else {
            SimpleVectorStore simple = SimpleVectorStore.builder(embeddingModel).build();
            this.fileStore = simple;
            this.vectorStore = simple;
            this.storePath = Paths.get(path);
            log.info("Vector store backend: SimpleVectorStore (local JSON file)");
            loadFromDisk();
        }
    }

    /** Embeds the document's chunks and records each chunk's vector id. */
    public void index(KnowledgeDocument document) {
        List<Document> vectors = new ArrayList<>();
        for (DocumentChunk chunk : document.getChunks()) {
            String id = UUID.randomUUID().toString();
            chunk.setVectorId(id);
            vectors.add(new Document(id, chunk.getText(), metadata(document, chunk)));
        }
        if (!vectors.isEmpty()) {
            vectorStore.add(vectors);
            persist();
        }
    }

    /** Removes the document's vectors (its database rows are removed by the caller). */
    public void remove(KnowledgeDocument document) {
        List<String> ids = document.getChunks().stream()
                .map(DocumentChunk::getVectorId)
                .filter(Objects::nonNull)
                .toList();
        removeVectors(ids);
    }

    public void removeVectors(List<String> ids) {
        if (!ids.isEmpty()) {
            vectorStore.delete(ids);
            persist();
        }
    }

    /**
     * The passages closest to the question, from the caller's own documents and the public
     * sample only. Numbered from 1 for citation.
     */
    public List<SourceHit> search(String question, String owner, int topK) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("A search always needs an owner");
        }
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .filterExpression(ownerFilter(owner))
                .build();
        List<Document> results = vectorStore.similaritySearch(request);
        List<SourceHit> hits = new ArrayList<>();
        for (Document d : results) {
            Map<String, Object> m = d.getMetadata();
            // Defence in depth: never return a passage the filter should have excluded.
            Object docOwner = m.get(OWNER);
            if (!owner.equals(docOwner) && !KnowledgeDocument.PUBLIC.equals(docOwner)) {
                continue;
            }
            hits.add(new SourceHit(hits.size() + 1, String.valueOf(m.get(DOCUMENT_ID)),
                    String.valueOf(m.get(FILE)), integer(m.get(PAGE)), d.getText(), d.getScore()));
        }
        return hits;
    }

    static Filter.Expression ownerFilter(String owner) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        return b.in(OWNER, owner, KnowledgeDocument.PUBLIC).build();
    }

    static Map<String, Object> metadata(KnowledgeDocument document, DocumentChunk chunk) {
        Map<String, Object> m = new HashMap<>();
        m.put(OWNER, document.getOwner());
        m.put(DOCUMENT_ID, document.getId());
        m.put(FILE, document.getFilename());
        m.put(POSITION, chunk.getPosition());
        if (chunk.getPage() != null) {
            m.put(PAGE, chunk.getPage());
        }
        return m;
    }

    /**
     * File-store mode only: the vectors that survived a restart, grouped by document, so the
     * database registry (lost when the in-memory database restarts) can be rebuilt without re-embedding anything.
     */
    public List<StoredDocument> storedDocuments() {
        if (storePath == null || !Files.exists(storePath)) {
            return List.of();
        }
        Map<String, StoredDocument> byId = new HashMap<>();
        try {
            JsonNode root = JsonMapper.builder().build().readTree(storePath.toFile());
            root.properties().forEach(entry -> {
                JsonNode node = entry.getValue();
                JsonNode meta = node.path("metadata");
                String documentId = meta.path(DOCUMENT_ID).asText(null);
                String owner = meta.path(OWNER).asText(null);
                if (documentId == null || owner == null) {
                    return; // legacy vector without an owner: never searchable, never restored
                }
                String text = node.hasNonNull("text") ? node.get("text").asText() : node.path("content").asText("");
                StoredDocument doc = byId.computeIfAbsent(documentId, id ->
                        new StoredDocument(id, owner, meta.path(FILE).asText("document"), new ArrayList<>()));
                doc.chunks().add(new StoredChunk(entry.getKey(), meta.path(POSITION).asInt(doc.chunks().size()),
                        meta.has(PAGE) ? meta.get(PAGE).asInt() : null, text));
            });
        } catch (Exception e) {
            log.warn("Could not read the vector-store file to restore the document list: {}", e.getClass().getSimpleName());
        }
        return new ArrayList<>(byId.values());
    }

    public record StoredDocument(String id, String owner, String file, List<StoredChunk> chunks) {
    }

    public record StoredChunk(String vectorId, int position, Integer page, String text) {
    }

    /** The underlying store. For maintenance and tests only: it has no owner filter. */
    public VectorStore vectorStore() {
        return vectorStore;
    }

    public boolean usesFileStore() {
        return fileStore != null;
    }

    private static Integer integer(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void persist() {
        if (fileStore != null) {
            saveToDisk();
        }
    }

    private void loadFromDisk() {
        try {
            if (Files.exists(storePath) && Files.size(storePath) > 4) {
                fileStore.load(storePath.toFile());
                log.info("Loaded the vector-store file");
            }
        } catch (Exception e) {
            log.warn("Could not load the vector-store file: {}", e.getClass().getSimpleName());
        }
    }

    private void saveToDisk() {
        try {
            Path parent = storePath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            fileStore.save(storePath.toFile());
        } catch (Exception e) {
            log.warn("Could not save the vector-store file: {}", e.getClass().getSimpleName());
        }
    }
}
