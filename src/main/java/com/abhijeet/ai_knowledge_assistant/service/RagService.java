package com.abhijeet.ai_knowledge_assistant.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagService {

    private final SimpleVectorStore vectorStore;

    @Autowired
    public RagService(EmbeddingModel embeddingModel) {
        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build();
    }

    public void loadPdf(Resource pdfResource) {
        PagePdfDocumentReader reader = new PagePdfDocumentReader(pdfResource);
        List<Document> documents = reader.get();
        vectorStore.add(documents);
    }

    public String search(String query) {
        SearchRequest request = SearchRequest.builder()
            .query(query)
            .topK(3)
            .build();
        List<Document> results = vectorStore.similaritySearch(request);
        return results.stream()
            .map(doc -> doc.getText())
            .collect(Collectors.joining("\n\n"));
    }
}