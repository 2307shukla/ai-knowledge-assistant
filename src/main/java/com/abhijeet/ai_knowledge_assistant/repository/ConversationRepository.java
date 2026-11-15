package com.abhijeet.ai_knowledge_assistant.repository;

import com.abhijeet.ai_knowledge_assistant.domain.Conversation;
import com.abhijeet.ai_knowledge_assistant.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByUserOrderByUpdatedAtDesc(User user);

    Optional<Conversation> findByIdAndUser(Long id, User user);
}
