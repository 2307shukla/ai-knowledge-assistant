package com.abhijeet.ai_knowledge_assistant.config;

import com.abhijeet.ai_knowledge_assistant.domain.Memory;
import com.abhijeet.ai_knowledge_assistant.domain.User;
import com.abhijeet.ai_knowledge_assistant.repository.MemoryRepository;
import com.abhijeet.ai_knowledge_assistant.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the demo account on startup so the public deployment is usable
 * without registration. Credentials are shown on the login page.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository users;
    private final MemoryRepository memories;
    private final PasswordEncoder encoder;

    public DemoDataSeeder(UserRepository users, MemoryRepository memories, PasswordEncoder encoder) {
        this.users = users;
        this.memories = memories;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByUsername("demo")) {
            return;
        }
        try {
            User demo = users.save(new User("demo", encoder.encode("demo123"), "Demo User"));
            memories.save(new Memory(demo,
                    "This is the shared demo account. The visitor is evaluating the app."));
            log.info("Seeded demo account (demo / demo123)");
        } catch (Exception e) {
            log.warn("Could not seed demo account: {}", e.getMessage());
        }
    }
}
