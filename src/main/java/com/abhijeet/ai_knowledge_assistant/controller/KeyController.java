package com.abhijeet.ai_knowledge_assistant.controller;

import com.abhijeet.ai_knowledge_assistant.domain.User;
import com.abhijeet.ai_knowledge_assistant.repository.UserRepository;
import com.abhijeet.ai_knowledge_assistant.service.ByokService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bring-your-own-key management.
 *
 * The key is write-only: this API accepts a key and later reports a masked hint.
 * There is no endpoint that returns the key itself, by design.
 */
@RestController
@RequestMapping("/api/keys")
public class KeyController {

    private final UserRepository users;
    private final ByokService byok;

    public KeyController(UserRepository users, ByokService byok) {
        this.users = users;
        this.byok = byok;
    }

    public record KeyRequest(String apiKey) {
    }

    private User current(Authentication authentication) {
        return users.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }

    @GetMapping
    public Map<String, Object> status(Authentication authentication) {
        User user = current(authentication);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("configured", user.hasOwnKey());
        body.put("hint", byok.hint(user));
        body.put("encryptionPersistent", byok.encryptionPersistent());
        return body;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> save(@RequestBody KeyRequest request,
                                                    Authentication authentication) {
        String apiKey = request.apiKey() == null ? "" : request.apiKey().trim();

        if (!apiKey.matches("[A-Za-z0-9_\\-]{20,300}")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "That does not look like an API key. "
                            + "Expected 20-300 characters of letters, digits, dash or underscore."));
        }

        User user = current(authentication);
        byok.save(user, apiKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("configured", true);
        body.put("hint", byok.hint(user));
        return ResponseEntity.ok(body);
    }

    @DeleteMapping
    public Map<String, Object> remove(Authentication authentication) {
        User user = current(authentication);
        byok.clear(user);
        return Map.of("configured", false);
    }
}
