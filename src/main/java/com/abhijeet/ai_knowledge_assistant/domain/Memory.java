package com.abhijeet.ai_knowledge_assistant.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;


import java.time.Instant;

/**
 * A durable fact the assistant should remember about a user across all
 * conversations - the same idea as Claude's memory feature, but explicit:
 * the user adds and removes entries themselves, so nothing is inferred
 * silently and no extra model call is needed to maintain it.
 */
@Table("memory")
public class Memory {

    @Id
    private Long id;

    /** Owner. A plain id: Spring Data JDBC keeps aggregates separate. */
    @Column("user_id")
    private Long userId;

    private String content;

    private Instant createdAt = Instant.now();

    public Memory() {
    }

    public Memory(User user, String content) {
        this.userId = user.getId();
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
