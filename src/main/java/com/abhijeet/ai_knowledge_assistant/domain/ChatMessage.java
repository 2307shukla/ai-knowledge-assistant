package com.abhijeet.ai_knowledge_assistant.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;


import java.time.Instant;

@Table("chat_message")
public class ChatMessage {

    @Id
    private Long id;

    @Column("conversation_id")
    private Long conversationId;

    /** "user" or "assistant". */
    private String role;

    private String content;

    private Instant createdAt = Instant.now();

    public ChatMessage() {
    }

    public ChatMessage(Conversation conversation, String role, String content) {
        this.conversationId = conversation.getId();
        this.role = role;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
