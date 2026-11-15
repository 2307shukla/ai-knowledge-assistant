package com.abhijeet.ai_knowledge_assistant.controller;

import com.abhijeet.ai_knowledge_assistant.domain.Conversation;
import com.abhijeet.ai_knowledge_assistant.domain.User;
import com.abhijeet.ai_knowledge_assistant.repository.ChatMessageRepository;
import com.abhijeet.ai_knowledge_assistant.repository.ConversationRepository;
import com.abhijeet.ai_knowledge_assistant.repository.MemoryRepository;
import com.abhijeet.ai_knowledge_assistant.repository.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregate analytics for administrators only.
 *
 * Access is enforced by Spring Security at the URL level (ROLE_ADMIN), not by
 * hiding the link in the UI. It exposes counts, never message content.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository users;
    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final MemoryRepository memories;

    public AdminController(UserRepository users,
                           ConversationRepository conversations,
                           ChatMessageRepository messages,
                           MemoryRepository memories) {
        this.users = users;
        this.conversations = conversations;
        this.messages = messages;
        this.memories = memories;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        List<User> allUsers = users.findAll();

        long totalConversations = 0;
        long totalMessages = 0;
        List<Map<String, Object>> perUser = new ArrayList<>();

        for (User user : allUsers) {
            List<Conversation> owned = conversations.findByUserOrderByUpdatedAtDesc(user);
            long userMessages = 0;

            for (Conversation conversation : owned) {
                userMessages += messages.findByConversationOrderByIdAsc(conversation).size();
            }

            totalConversations += owned.size();
            totalMessages += userMessages;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("username", user.getUsername());
            row.put("role", user.getRole());
            row.put("conversations", owned.size());
            row.put("messages", userMessages);
            perUser.add(row);
        }

        perUser.sort((a, b) -> Long.compare((long) b.get("messages"), (long) a.get("messages")));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("users", allUsers.size());
        body.put("conversations", totalConversations);
        body.put("messages", totalMessages);
        body.put("memories", memories.count());
        body.put("avgMessagesPerConversation",
                totalConversations == 0 ? 0 : Math.round((double) totalMessages / totalConversations));
        body.put("perUser", perUser);
        return body;
    }
}
