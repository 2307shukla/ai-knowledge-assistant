package com.abhijeet.ai_knowledge_assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Document store backed by PgVectorStore (Neon Postgres + pgvector).
 * Spring Boot auto-configures PgVectorStore when spring-ai-pgvector-store
 * is on the classpath and SPRING_DATASOURCE_* env vars are set.
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final VectorStore vectorStore;

    public RagService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        log.info("RagService initialised with {}", vectorStore.getClass().getSimpleName());
    }

    /** Extract text from a PDF without touching the vector store. */
    public List<Document> readPdf(Resource resource) {
        return new PagePdfDocumentReader(resource).get();
    }

    /** Add chunks to the store. */
    public int index(List<Document> documents) {
        vectorStore.add(documents);
        log.info("Indexed {} document chunks", documents.size());
        return documents.size();
    }

    /** Read and index a PDF in one step. */
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
}
