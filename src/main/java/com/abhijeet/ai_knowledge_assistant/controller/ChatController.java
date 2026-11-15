package com.abhijeet.ai_knowledge_assistant.controller;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private OllamaChatModel chatModel;

    @GetMapping("/ask")
    public String ask(@RequestParam String question) {
        return chatModel.call(question);
    }
}