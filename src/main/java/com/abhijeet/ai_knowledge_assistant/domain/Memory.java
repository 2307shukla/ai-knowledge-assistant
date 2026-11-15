package com.abhijeet.ai_knowledge_assistant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A durable fact the assistant should remember about a user across all
 * conversations - the same idea as Claude's memory feature, but explicit:
 * the user adds and removes entries themselves, so nothing is inferred
 * silently and no extra model call is needed to maintain it.
 */
@Entity
@Table(name = "memory")
public class Memory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Memory() {
    }

    public Memory(User user, String content) {
        this.user = user;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
