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
 * Google OAuth2 registration, built in code rather than from properties.
 *
 * Why: the registration must only exist when credentials are actually present.
 * Declaring it in application.properties with empty defaults would create a
 * half-configured client, and the login button would fail at runtime instead of
 * simply not appearing.
 *
 * userNameAttributeName is "email" so the authenticated principal name equals
 * the email address, which is also the User.username - that keeps every
 * existing per-user query working without special cases.
 *
 * A malformed configuration degrades to "Google login unavailable" instead of
 * taking the whole application down: an optional sign-in method should never be
 * able to stop the site from starting.
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
            return new InMemoryClientRegistrationRepository();
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
                    e.getMessage());
            return new InMemoryClientRegistrationRepository();
        }
    }
}
