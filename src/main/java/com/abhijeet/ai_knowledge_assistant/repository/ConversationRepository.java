package com.abhijeet.ai_knowledge_assistant.repository;

import com.abhijeet.ai_knowledge_assistant.domain.Conversation;
import com.abhijeet.ai_knowledge_assistant.domain.User;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends ListCrudRepository<Conversation, Long> {

    List<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<Conversation> findByIdAndUserId(Long id, Long userId);

    default List<Conversation> findByUserOrderByUpdatedAtDesc(User user) {
        return findByUserIdOrderByUpdatedAtDesc(user.getId());
    }

    /** A conversation only if it belongs to this user: the authorization check for every chat path. */
    default Optional<Conversation> findByIdAndUser(Long id, User user) {
        return findByIdAndUserId(id, user.getId());
    }
}
