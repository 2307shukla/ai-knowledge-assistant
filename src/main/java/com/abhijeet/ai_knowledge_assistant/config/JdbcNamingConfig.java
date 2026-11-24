package com.abhijeet.ai_knowledge_assistant.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;

/**
 * Spring Data JDBC quotes table and column names by default, which makes them case-sensitive:
 * HSQLDB stores unquoted names in upper case and PostgreSQL in lower case, so a quoted
 * "app_user" matches neither everywhere. Unquoted names fold the same way on both sides, so
 * schema.sql and the generated SQL agree on every database. None of our names is a reserved word.
 */
@Configuration
public class JdbcNamingConfig {

    @Bean
    static BeanPostProcessor unquotedJdbcNames() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof RelationalMappingContext context) {
                    context.setForceQuote(false);
                }
                return bean;
            }
        };
    }
}
