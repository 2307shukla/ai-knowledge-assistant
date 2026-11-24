package com.abhijeet.ai_knowledge_assistant.repository;

import com.abhijeet.ai_knowledge_assistant.domain.Memory;
import com.abhijeet.ai_knowledge_assistant.domain.User;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;

public interface MemoryRepository extends ListCrudRepository<Memory, Long> {

    List<Memory> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Memory> findByIdAndUserId(Long id, Long userId);

    default List<Memory> findByUserOrderByCreatedAtDesc(User user) {
        return findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    default Optional<Memory> findByIdAndUser(Long id, User user) {
        return findByIdAndUserId(id, user.getId());
    }
}
