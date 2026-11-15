package com.abhijeet.ai_knowledge_assistant.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI assistantOpenApi() {
        return new OpenAPI().info(new Info()
                .title("AI Knowledge Assistant API")
                .version("1.0.0")
                .description("""
                        Spring Boot + Spring AI assistant with session login, per-user
                        conversations, explicit memory and streamed (SSE) responses.

                        Authentication uses a session cookie from the form login at
                        /login.html, so try the endpoints in the UI or with a cookie jar.
                        """));
    }
}
