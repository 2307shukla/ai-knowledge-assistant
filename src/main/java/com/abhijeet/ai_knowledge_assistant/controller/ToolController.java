package com.abhijeet.ai_knowledge_assistant.controller;

import com.abhijeet.ai_knowledge_assistant.domain.User;
import com.abhijeet.ai_knowledge_assistant.repository.ConversationRepository;
import com.abhijeet.ai_knowledge_assistant.repository.MemoryRepository;
import com.abhijeet.ai_knowledge_assistant.repository.UserRepository;
import com.abhijeet.ai_knowledge_assistant.tools.AssistantTools;
import com.abhijeet.ai_knowledge_assistant.util.InputSanitizer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Function calling demo: the model decides when to call a Java method, the
 * method runs locally, and the result is fed back so the model can answer.
 */
@RestController
@RequestMapping("/api/tools")
public class ToolController {

    private final ChatClient chatClient;
    private final UserRepository users;
    private final MemoryRepository memories;
    private final ConversationRepository conversations;

    public ToolController(ChatClient chatClient,
                          UserRepository users,
                          MemoryRepository memories,
                          ConversationRepository conversations) {
        this.chatClient = chatClient;
        this.users = users;
        this.memories = memories;
        this.conversations = conversations;
    }

    public record ToolRequest(String question) {
    }

    @PostMapping("/ask")
    public Map<String, String> ask(@RequestBody ToolRequest request, Authentication authentication) {
        String question = InputSanitizer.clean(request.question(), 500);
        if (question.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question is empty.");
        }

        User user = users.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        String answer = chatClient.prompt()
                .system("""
                        You are a helpful assistant with access to tools.
                        Call a tool when it gives you a factual answer you do not already know.
                        Never invent values a tool could return.
                        """)
                .user(question)
                .tools(new AssistantTools(user, memories, conversations))
                .call()
                .content();

        return Map.of("answer", answer == null ? "" : answer);
    }
}
