package com.abhijeet.ai_knowledge_assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Document store backed by PgVectorStore when PostgreSQL is configured, otherwise by a JSON file on disk.
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final String VECTOR_TABLE = "vector_store";

    private final VectorStore vectorStore;
    private final SimpleVectorStore fileStore;
    private final Path storePath;
    private final JdbcTemplate jdbcTemplate;
    private final AtomicInteger added = new AtomicInteger();
    private volatile boolean loadedFromDisk;

    public RagService(EmbeddingModel embeddingModel,
                      ObjectProvider<VectorStore> providedStore,
                      JdbcTemplate jdbcTemplate,
                      @Value("${app.vector-store.path:./data/vector-store.json}") String path) {

        this.jdbcTemplate = jdbcTemplate;
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
            log.info("Vector store backend: SimpleVectorStore at {}", storePath.toAbsolutePath());
            loadFromDisk();
        }
    }

    public List<Document> readPdf(Resource resource) {
        return new PagePdfDocumentReader(resource).get();
    }

    public int index(List<Document> documents) {
        vectorStore.add(documents);
        added.addAndGet(documents.size());
        if (fileStore != null) {
            saveToDisk();
        }
        return documents.size();
    }

    public String loadPdf(Resource resource) {
        List<Document> documents = readPdf(resource);
        index(documents);
        return documents.size() + " chunks indexed";
    }

    /** Index plain text, splitting on blank lines into paragraph-sized chunks. */
    public int indexText(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        List<Document> documents = Arrays.stream(text.split("\\n\\s*\\n"))
                .map(String::trim)
                .filter(paragraph -> paragraph.length() > 40)
                .map(Document::new)
                .collect(Collectors.toList());

        return index(documents);
    }

    /** True when there is nothing to search yet. */
    public boolean isEmpty() {
        if (fileStore != null) {
            return !loadedFromDisk && added.get() == 0;
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + VECTOR_TABLE, Integer.class);
            return count == null || count == 0;
        } catch (Exception e) {
            // table not created yet on a fresh database
            return true;
        }
    }

    public String search(String query) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(3)
                .build();
        List<Document> results = vectorStore.similaritySearch(request);
        return results.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
    }

    private void loadFromDisk() {
        try {
            if (Files.exists(storePath) && Files.size(storePath) > 4) {
                fileStore.load(storePath.toFile());
                loadedFromDisk = true;
                log.info("Loaded vector store from {}", storePath.toAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("Could not load vector store from {}: {}", storePath, e.getMessage());
        }
    }

    private void saveToDisk() {
        try {
            Path parent = storePath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            fileStore.save(storePath.toFile());
            log.info("Saved vector store to {}", storePath.toAbsolutePath());
        } catch (Exception e) {
            log.warn("Could not save vector store to {}: {}", storePath, e.getMessage());
        }
    }
}
