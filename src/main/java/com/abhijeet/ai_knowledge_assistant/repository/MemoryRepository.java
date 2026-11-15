package com.abhijeet.ai_knowledge_assistant.repository;

import com.abhijeet.ai_knowledge_assistant.domain.Memory;
import com.abhijeet.ai_knowledge_assistant.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemoryRepository extends JpaRepository<Memory, Long> {

    List<Memory> findByUserOrderByCreatedAtDesc(User user);

    Optional<Memory> findByIdAndUser(Long id, User user);
}
