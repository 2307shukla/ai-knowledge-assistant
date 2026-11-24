package com.abhijeet.ai_knowledge_assistant.config;

import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Swaps the SDK-based embedding model that Spring AI auto-configures for {@link HttpEmbeddingModel},
 * keeping the same settings (base URL, key and model). Other embedding models, such as the test fake,
 * are left alone.
 */
@Configuration
public class HttpEmbeddingConfig {

    @Bean
    static BeanPostProcessor httpEmbeddingSwap(Environment env) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof OpenAiEmbeddingModel) {
                    return new HttpEmbeddingModel(
                            env.getProperty("spring.ai.openai.base-url", "https://generativelanguage.googleapis.com/v1beta/openai"),
                            env.getProperty("spring.ai.openai.api-key", "unset"),
                            env.getProperty("spring.ai.openai.embedding.options.model", "gemini-embedding-001"),
                            env.getProperty("spring.ai.vectorstore.pgvector.dimensions", Integer.class, 768));
                }
                return bean;
            }
        };
    }
}
