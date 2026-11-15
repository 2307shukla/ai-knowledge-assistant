package com.abhijeet.ai_knowledge_assistant.controller;

import com.abhijeet.ai_knowledge_assistant.service.RagService;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    @Autowired
    private RagService ragService;

    @Autowired
    private OpenAiChatModel chatModel;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadPdf(@RequestParam("file") MultipartFile file) {
        try {
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() { return file.getOriginalFilename(); }
            };
            ragService.loadPdf(resource);
            return ResponseEntity.ok("PDF uploaded and indexed successfully!");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/ask")
    public ResponseEntity<String> ask(@RequestParam String question) {
        String context = ragService.search(question);
        if (context.isBlank()) {
            return ResponseEntity.ok("No relevant documents found. Please upload a PDF first.");
        }
        String prompt = "Based on the following context, answer the question.\n\n" +
                "Context:\n" + context + "\n\n" +
                "Question: " + question;
        String answer = chatModel.call(prompt);
        return ResponseEntity.ok(answer);
    }
}