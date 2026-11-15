package com.abhijeet.ai_knowledge_assistant.service;

import com.abhijeet.ai_knowledge_assistant.domain.ChatMessage;
import com.abhijeet.ai_knowledge_assistant.domain.Conversation;
import com.abhijeet.ai_knowledge_assistant.repository.ChatMessageRepository;
import com.abhijeet.ai_knowledge_assistant.repository.ConversationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists messages in their own transaction.
 *
 * Streaming runs on a worker thread outside the request's transaction, so a
 * detached conversation entity cannot safely be saved there. Reloading inside
 * a short transaction keeps the JPA session boundary correct.
 */
@Service
public class ChatPersistence {

    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;

    public ChatPersistence(ConversationRepository conversations, ChatMessageRepository messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    @Transactional
    public void append(Long conversationId, String role, String content, String titleFrom) {
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new IllegalStateException("Conversation not found: " + conversationId));

        messages.save(new ChatMessage(conversation, role, content));

        if (titleFrom != null && "New chat".equals(conversation.getTitle())) {
            conversation.setTitle(titleFrom.length() > 60
                    ? titleFrom.substring(0, 60) + "..."
                    : titleFrom);
        }
        conversation.touch();
        conversations.save(conversation);
    }
}
