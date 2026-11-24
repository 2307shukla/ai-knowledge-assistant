package com.abhijeet.ai_knowledge_assistant.repository;

import com.abhijeet.ai_knowledge_assistant.domain.KnowledgeDocument;
import org.springframework.data.repository.ListCrudRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Every lookup takes an owner, so a caller can never load another user's document by id.
 * Inserts and updates go through JdbcAggregateTemplate (ids are assigned in Java).
 */
public interface KnowledgeDocumentRepository extends ListCrudRepository<KnowledgeDocument, String> {

    List<KnowledgeDocument> findByOwnerInOrderByCreatedAtDesc(Collection<String> owners);

    Optional<KnowledgeDocument> findByIdAndOwner(String id, String owner);

    long countByOwner(String owner);
}
