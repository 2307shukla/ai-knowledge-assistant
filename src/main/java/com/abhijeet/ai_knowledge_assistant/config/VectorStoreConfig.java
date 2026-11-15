package com.abhijeet.ai_knowledge_assistant.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Uses PgVectorStore when the application is running against PostgreSQL, and
 * lets RagService fall back to the file-backed SimpleVectorStore otherwise
 * (for example on H2 during local development).
 *
 * The condition keys off the datasource URL rather than a separate flag, so
 * pointing SPRING_DATASOURCE_URL at Neon is enough to switch storage - there is
 * no second setting to forget.
 */
@Configuration
public class VectorStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreConfig.class);

    @Bean
    @ConditionalOnExpression("'${spring.datasource.url:}'.contains('postgresql')")
    public VectorStore pgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        log.info("PostgreSQL detected - using PgVectorStore for document embeddings");
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .initializeSchema(true)
                .vectorTableName("vector_store")
                .build();
    }
}
