package com.abhijeet.ai_knowledge_assistant.service;

import com.abhijeet.ai_knowledge_assistant.domain.User;
import com.abhijeet.ai_knowledge_assistant.repository.UserRepository;
import com.abhijeet.ai_knowledge_assistant.security.SecretCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Per-user provider keys, stored AES-GCM encrypted and never returned by the API.
 */
@Service
public class ByokService {

    private static final Logger log = LoggerFactory.getLogger(ByokService.class);

    private final UserRepository users;
    private final SecretCipher cipher;
    private final String baseUrl;
    private final String model;

    public ByokService(UserRepository users,
                       SecretCipher cipher,
                       @Value("${spring.ai.openai.base-url}") String baseUrl,
                       @Value("${spring.ai.openai.chat.options.model}") String model) {
        this.users = users;
        this.cipher = cipher;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    public void save(User user, String apiKey) {
        user.setApiKeyEncrypted(cipher.encrypt(apiKey.trim()));
        users.save(user);
        log.info("Stored BYOK for user id={}", user.getId());
    }

    public void clear(User user) {
        user.setApiKeyEncrypted(null);
        users.save(user);
        log.info("Cleared BYOK for user id={}", user.getId());
    }

    /** Masked hint such as ****a1b2, or null when no key is stored. */
    public String hint(User user) {
        if (!user.hasOwnKey()) {
            return null;
        }
        try {
            String plain = cipher.decrypt(user.getApiKeyEncrypted());
            return plain.length() <= 4 ? "****" : "****" + plain.substring(plain.length() - 4);
        } catch (Exception e) {
            return "****";
        }
    }

    public boolean encryptionPersistent() {
        return cipher.isPersistent();
    }

    /** A model bound to the user's key, or null when they have not set one. */
    public ChatModel modelFor(User user) {
        if (!user.hasOwnKey()) {
            return null;
        }
        try {
            String plain = cipher.decrypt(user.getApiKeyEncrypted());
            // Spring AI 2 talks to the provider through the official OpenAI SDK, which appends
            // /chat/completions to the base URL (Gemini's OpenAI-compatible endpoint has no /v1).
            return OpenAiChatModel.builder()
                    .options(OpenAiChatOptions.builder()
                            .baseUrl(baseUrl)
                            .apiKey(plain)
                            .model(model)
                            .temperature(0.7)
                            .build())
                    .build();
        } catch (Exception e) {
            // Deliberately does not include the key in the message.
            log.warn("Could not use stored BYOK for user id={}: {}", user.getId(), e.getClass().getSimpleName());
            return null;
        }
    }
}
