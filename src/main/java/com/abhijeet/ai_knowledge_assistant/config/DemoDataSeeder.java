package com.abhijeet.ai_knowledge_assistant.config;

import com.abhijeet.ai_knowledge_assistant.domain.Memory;
import com.abhijeet.ai_knowledge_assistant.domain.User;
import com.abhijeet.ai_knowledge_assistant.repository.MemoryRepository;
import com.abhijeet.ai_knowledge_assistant.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Creates the demo account on startup so the public deployment is usable
 * without registration. Its credentials are shown on the login page.
 *
 * <p>No administrator exists unless the operator sets {@code APP_ADMIN_PASSWORD}. An old
 * "admin" account that still has the retired, publicly documented password is locked.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository users;
    private final MemoryRepository memories;
    private final PasswordEncoder encoder;
    private final String adminPassword;

    /** The password that used to be published for the admin account; it must never work again. */
    private static final String RETIRED_ADMIN_PASSWORD = "admin123";
    private static final int MIN_ADMIN_PASSWORD_LENGTH = 12;

    public DemoDataSeeder(UserRepository users, MemoryRepository memories, PasswordEncoder encoder,
                          @Value("${app.admin-password:}") String adminPassword) {
        this.users = users;
        this.memories = memories;
        this.encoder = encoder;
        this.adminPassword = adminPassword == null ? "" : adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureAdmin();

        if (users.existsByUsername("demo")) {
            return;
        }
        try {
            User demo = users.save(new User("demo", encoder.encode("demo123"), "Demo User"));
            memories.save(new Memory(demo,
                    "This is the shared demo account. The visitor is evaluating the app."));
            log.info("Seeded demo account (demo / demo123)");
        } catch (Exception e) {
            log.warn("Could not seed demo account: {}", e.getClass().getSimpleName());
        }
    }

    private void ensureAdmin() {
        try {
            Optional<User> existing = users.findByUsername("admin");
            if (adminPassword.isBlank()) {
                existing.filter(a -> encoder.matches(RETIRED_ADMIN_PASSWORD, a.getPassword())).ifPresent(a -> {
                    a.setPassword(encoder.encode(UUID.randomUUID().toString()));
                    a.setRole("USER");
                    users.save(a);
                    log.warn("Locked a leftover admin account that still used the retired published password");
                });
                return;
            }
            if (adminPassword.length() < MIN_ADMIN_PASSWORD_LENGTH) {
                log.warn("APP_ADMIN_PASSWORD is shorter than {} characters; no admin account was created",
                        MIN_ADMIN_PASSWORD_LENGTH);
                return;
            }
            if (existing.isEmpty()) {
                User admin = new User("admin", encoder.encode(adminPassword), "Administrator");
                admin.setRole("ADMIN");
                users.save(admin);
                log.info("Created the admin account from APP_ADMIN_PASSWORD");
            }
        } catch (Exception e) {
            log.warn("Could not set up the admin account: {}", e.getClass().getSimpleName());
        }
    }
}
