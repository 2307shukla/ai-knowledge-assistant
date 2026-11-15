package com.abhijeet.ai_knowledge_assistant.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagService {

    private final SimpleVectorStore vectorStore;

    public RagService(EmbeddingModel embeddingModel) {
        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build();
    }

    /** Extract text from a PDF without touching the vector store. */
    public List<Document> readPdf(Resource resource) {
        return new PagePdfDocumentReader(resource).get();
    }

    /** Add already-read chunks to the vector store. */
    public int index(List<Document> documents) {
        vectorStore.add(documents);
        return documents.size();
    }

    /** Read and index in one step. */
    public String loadPdf(Resource resource) {
        List<Document> documents = readPdf(resource);
        index(documents);
        return documents.size() + " chunks indexed";
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
