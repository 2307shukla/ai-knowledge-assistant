package com.abhijeet.ai_knowledge_assistant.controller;

import com.abhijeet.ai_knowledge_assistant.domain.Memory;
import com.abhijeet.ai_knowledge_assistant.domain.User;
import com.abhijeet.ai_knowledge_assistant.repository.MemoryRepository;
import com.abhijeet.ai_knowledge_assistant.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/memories")
public class MemoryController {

    private static final int MAX_MEMORY_LENGTH = 500;

    private final MemoryRepository memories;
    private final UserRepository users;

    public MemoryController(MemoryRepository memories, UserRepository users) {
        this.memories = memories;
        this.users = users;
    }

    private User currentUser(Authentication authentication) {
        return users.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user not found: " + authentication.getName()));
    }

    public record MemoryRequest(String content) {
    }

    @GetMapping
    public List<Map<String, Object>> list(Authentication authentication) {
        return memories.findByUserOrderByCreatedAtDesc(currentUser(authentication)).stream()
                .map(memory -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", memory.getId());
                    row.put("content", memory.getContent());
                    return row;
                })
                .toList();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> add(@RequestBody MemoryRequest request,
                                                   Authentication authentication) {
        String content = request.content() == null ? "" : request.content().trim();
        if (content.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Memory cannot be empty."));
        }
        if (content.length() > MAX_MEMORY_LENGTH) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Memory too long (max " + MAX_MEMORY_LENGTH + " characters)."));
        }

        Memory saved = memories.save(new Memory(currentUser(authentication), content));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", saved.getId());
        body.put("content", saved.getContent());
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id,
                                                      Authentication authentication) {
        Memory memory = memories.findByIdAndUser(id, currentUser(authentication)).orElse(null);
        if (memory == null) {
            return ResponseEntity.notFound().build();
        }
        memories.delete(memory);
        return ResponseEntity.ok(Map.of("deleted", true));
    }
}
