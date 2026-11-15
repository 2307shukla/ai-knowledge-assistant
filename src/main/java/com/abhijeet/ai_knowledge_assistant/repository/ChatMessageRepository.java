package com.abhijeet.ai_knowledge_assistant.repository;

import com.abhijeet.ai_knowledge_assistant.domain.ChatMessage;
import com.abhijeet.ai_knowledge_assistant.domain.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationOrderByIdAsc(Conversation conversation);

    /** Most recent messages first - used to build the model's working context. */
    List<ChatMessage> findTop12ByConversationOrderByIdDesc(Conversation conversation);
}
