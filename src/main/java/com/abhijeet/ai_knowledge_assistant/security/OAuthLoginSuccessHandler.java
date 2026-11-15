package com.abhijeet.ai_knowledge_assistant.security;

import com.abhijeet.ai_knowledge_assistant.domain.User;
import com.abhijeet.ai_knowledge_assistant.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Creates a local account the first time someone signs in with Google.
 *
 * The account is keyed on the verified email, so an existing password account
 * with the same address keeps its conversations and memories. A random
 * password hash is stored because form login must not work for an account
 * that was created through an external identity provider.
 */
@Component
public class OAuthLoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuthLoginSuccessHandler.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public OAuthLoginSuccessHandler(UserRepository users, PasswordEncoder encoder) {
        super("/chat.html");
        setAlwaysUseDefaultTargetUrl(true);
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        if (authentication instanceof OAuth2AuthenticationToken token) {
            OAuth2User principal = token.getPrincipal();
            String email = asText(principal.getAttributes().get("email"));
            String name = asText(principal.getAttributes().get("name"));

            if (email != null && !users.existsByUsername(email)) {
                try {
                    User user = new User(
                            email,
                            encoder.encode(UUID.randomUUID().toString()),
                            name == null || name.isBlank() ? email : name);
                    users.save(user);
                    log.info("Created account for Google sign-in: {}", email);
                } catch (Exception e) {
                    log.warn("Could not create account for {}: {}", email, e.getMessage());
                }
            }
        }

        super.onAuthenticationSuccess(request, response, authentication);
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
