package com.abhijeet.ai_knowledge_assistant.repository;

import com.abhijeet.ai_knowledge_assistant.domain.ChatMessage;
import com.abhijeet.ai_knowledge_assistant.domain.Conversation;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends ListCrudRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByIdAsc(Long conversationId);

    /** The most recent messages, newest first (the prompt history window). */
    @Query("SELECT * FROM chat_message WHERE conversation_id = :conversationId ORDER BY id DESC LIMIT 12")
    List<ChatMessage> findRecent(@Param("conversationId") Long conversationId);

    @Modifying
    @Query("DELETE FROM chat_message WHERE conversation_id = :conversationId")
    int deleteByConversationId(@Param("conversationId") Long conversationId);

    default List<ChatMessage> findByConversationOrderByIdAsc(Conversation conversation) {
        return findByConversationIdOrderByIdAsc(conversation.getId());
    }

    default List<ChatMessage> findTop12ByConversationOrderByIdDesc(Conversation conversation) {
        return findRecent(conversation.getId());
    }
}
