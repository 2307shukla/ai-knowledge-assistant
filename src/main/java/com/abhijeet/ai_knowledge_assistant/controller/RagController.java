package com.abhijeet.ai_knowledge_assistant.controller;

import com.abhijeet.ai_knowledge_assistant.service.RagService;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private static final int PREVIEW_LENGTH = 800;

    private final RagService ragService;
    private final OpenAiChatModel chatModel;

    public RagController(RagService ragService, OpenAiChatModel chatModel) {
        this.ragService = ragService;
        this.chatModel = chatModel;
    }

    /** Upload a PDF, extract its text, and index it for later questions. */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        try {
            String filename = file.getOriginalFilename();
            List<Document> documents;
            String text;

            if (filename != null && filename.toLowerCase().endsWith(".txt")) {
                text = new String(file.getBytes());
                int chunks = ragService.indexText(text);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("file", filename);
                body.put("chunks", chunks);
                body.put("characters", text.length());
                body.put("indexed", true);
                body.put("preview", text.length() > PREVIEW_LENGTH ? text.substring(0, PREVIEW_LENGTH) + "..." : text);
                return ResponseEntity.ok(body);
            }

            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            documents = ragService.readPdf(resource);
            ragService.index(documents);

            text = documents.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n"));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("file", file.getOriginalFilename());
            body.put("chunks", documents.size());
            body.put("characters", text.length());
            body.put("indexed", true);
            body.put("preview", text.length() > PREVIEW_LENGTH
                    ? text.substring(0, PREVIEW_LENGTH) + "..."
                    : text);
            return ResponseEntity.ok(body);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Could not read the document: " + e.getMessage()));
        }
    }

    @GetMapping("/ask")
    public ResponseEntity<Map<String, Object>> ask(@RequestParam String question) {
        String clean = com.abhijeet.ai_knowledge_assistant.util.InputSanitizer.clean(question, 500);
        if (clean.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Question is empty."));
        }

        String context = ragService.search(clean);

        if (context.isBlank()) {
            return ResponseEntity.ok(Map.of(
                    "message", "No relevant documents found. Add a PDF first."));
        }

        String prompt = "Based on the following context, answer the question.\n\n"
                + "Context:\n" + context + "\n\n"
                + "Question: " + clean;

        String answer = chatModel.call(prompt);

        double overlap = com.abhijeet.ai_knowledge_assistant.util.GroundingCheck
                .overlap(answer, context);
        boolean grounded = overlap >= 0.45;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("answer", answer);
        body.put("grounded", grounded);
        body.put("overlap", Math.round(overlap * 100) / 100.0);
        if (!grounded) {
            body.put("warning", "The answer does not closely match the retrieved document text. "
                    + "Treat it as unverified.");
        }
        return ResponseEntity.ok(body);
    }
}

