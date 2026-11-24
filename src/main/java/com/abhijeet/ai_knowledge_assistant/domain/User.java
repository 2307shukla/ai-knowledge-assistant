package com.abhijeet.ai_knowledge_assistant.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;


import java.time.Instant;

@Table("app_user")
public class User {

    @Id
    private Long id;

    private String username;

    private String password;

    private String displayName;

    /** USER or ADMIN. Enforced by Spring Security, not by the UI. */
    private String role = "USER";

    /**
     * User-supplied AI provider key (BYOK), AES-GCM encrypted. Never returned to
     * the client and never logged - only a masked hint is exposed.
     */
    private String apiKeyEncrypted;

    private Instant createdAt = Instant.now();

    public User() {
    }

    public User(String username, String password, String displayName) {
        this.username = username;
        this.password = password;
        this.displayName = displayName;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getApiKeyEncrypted() {
        return apiKeyEncrypted;
    }

    public void setApiKeyEncrypted(String apiKeyEncrypted) {
        this.apiKeyEncrypted = apiKeyEncrypted;
    }

    public boolean hasOwnKey() {
        return apiKeyEncrypted != null && !apiKeyEncrypted.isBlank();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
