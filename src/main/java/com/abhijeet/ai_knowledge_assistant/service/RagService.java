package com.abhijeet.ai_knowledge_assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Document store with file-backed persistence.
 *
 * The vector store is written to disk after every index operation and reloaded
 * on startup, so embeddings are computed once and reused instead of being
 * re-generated (and re-billed) on every boot.
 *
 * Limitation to be aware of: Render's free tier has an ephemeral filesystem, so
 * this survives restarts within a running instance but not a redeploy or a
 * sleep/wake cycle. For true persistence, point this at a mounted volume or
 * switch to PgVectorStore backed by a free Postgres instance.
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final SimpleVectorStore vectorStore;
    private final Path storePath;
    private final AtomicInteger added = new AtomicInteger();
    private volatile boolean loadedFromDisk;

    public RagService(EmbeddingModel embeddingModel,
                      @Value("${app.vector-store.path:./data/vector-store.json}") String path) {
        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        this.storePath = Paths.get(path);
        loadFromDisk();
    }

    /** Extract text from a PDF without touching the vector store. */
    public List<Document> readPdf(Resource resource) {
        return new PagePdfDocumentReader(resource).get();
    }

    /** Add chunks to the store and persist immediately. */
    public int index(List<Document> documents) {
        vectorStore.add(documents);
        added.addAndGet(documents.size());
        saveToDisk();
        return documents.size();
    }

    /** Read and index in one step. */
    public String loadPdf(Resource resource) {
        List<Document> documents = readPdf(resource);
        index(documents);
        return documents.size() + " chunks indexed";
    }

    /** True when there is nothing to search yet. */
    public boolean isEmpty() {
        return !loadedFromDisk && added.get() == 0;
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
                vectorStore.load(storePath.toFile());
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
            vectorStore.save(storePath.toFile());
            log.info("Saved vector store to {}", storePath.toAbsolutePath());
        } catch (Exception e) {
            log.warn("Could not save vector store to {}: {}", storePath, e.getMessage());
        }
    }
}
