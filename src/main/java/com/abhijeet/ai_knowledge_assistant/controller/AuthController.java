package com.abhijeet.ai_knowledge_assistant.controller;

import com.abhijeet.ai_knowledge_assistant.domain.User;
import com.abhijeet.ai_knowledge_assistant.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AuthController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public record RegisterRequest(String username, String password, String displayName) {
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        String username = request.username() == null ? "" : request.username().trim();
        String password = request.password() == null ? "" : request.password();

        if (username.length() < 3) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Username must be at least 3 characters."));
        }
        if (!username.matches("[A-Za-z0-9._-]+")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Username may only contain letters, digits, dot, dash, underscore."));
        }
        if (password.length() < 6) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Password must be at least 6 characters."));
        }
        if (users.existsByUsername(username)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "That username is already taken."));
        }

        String display = request.displayName() == null || request.displayName().isBlank()
                ? username
                : request.displayName().trim();

        User saved = users.save(new User(username, encoder.encode(password), display));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", saved.getUsername());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = users.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", user.getUsername());
        body.put("displayName", user.getDisplayName());
        return ResponseEntity.ok(body);
    }
}
