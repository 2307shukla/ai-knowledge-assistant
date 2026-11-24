package com.abhijeet.ai_knowledge_assistant.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * Google OAuth2 registration. Built in code so the client only exists when credentials are present.
 */
@Configuration
public class OAuthConfig {

    private static final Logger log = LoggerFactory.getLogger(OAuthConfig.class);

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${GOOGLE_CLIENT_ID:}") String clientId,
            @Value("${GOOGLE_CLIENT_SECRET:}") String clientSecret) {

        if (clientId.isBlank() || clientSecret.isBlank()) {
            log.info("Google OAuth is not configured (GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET absent)");
            return registrationId -> null;
        }

        try {
            ClientRegistration google = ClientRegistration.withRegistrationId("google")
                    .clientId(clientId.trim())
                    .clientSecret(clientSecret.trim())
                    .clientName("Google")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "profile", "email")
                    .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                    .tokenUri("https://oauth2.googleapis.com/token")
                    .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                    .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                    .userNameAttributeName("email")
                    .build();

            log.info("Google OAuth configured");
            return new InMemoryClientRegistrationRepository(google);

        } catch (Exception e) {
            log.error("Google OAuth configuration is invalid, disabling Google login: {}",
                    e.getClass().getSimpleName());
            return registrationId -> null;
        }
    }
}
