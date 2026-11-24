package com.abhijeet.ai_knowledge_assistant.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;


import java.time.Instant;

@Table("conversation")
public class Conversation {

    @Id
    private Long id;

    /** Owner. A plain id: Spring Data JDBC keeps aggregates separate. */
    @Column("user_id")
    private Long userId;

    private String title = "New chat";

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    public Conversation() {
    }

    public Conversation(User user) {
        this.userId = user.getId();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
