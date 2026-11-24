package com.abhijeet.ai_knowledge_assistant;

import com.abhijeet.ai_knowledge_assistant.config.DemoDataSeeder;
import com.abhijeet.ai_knowledge_assistant.domain.User;
import com.abhijeet.ai_knowledge_assistant.repository.MemoryRepository;
import com.abhijeet.ai_knowledge_assistant.repository.UserRepository;
import com.abhijeet.ai_knowledge_assistant.support.FakeAiConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/** There is no default administrator, and the old published admin password never works again. */
@SpringBootTest(properties = "app.vector-store.path=target/test-vectors/admin-${random.uuid}.json")
@Import(FakeAiConfig.class)
class DefaultAdminTest {

    @Autowired
    private UserRepository users;
    @Autowired
    private MemoryRepository memories;
    @Autowired
    private PasswordEncoder encoder;

    private void removeAdmin() {
        users.findByUsername("admin").ifPresent(users::delete);
    }

    @Test
    void noAdminExistsWithoutAnOperatorPassword() {
        removeAdmin();
        new DemoDataSeeder(users, memories, encoder, "").run(null);
        assertThat(users.findByUsername("admin")).isEmpty();
    }

    @Test
    void aLeftoverAdminWithTheRetiredPasswordIsLocked() {
        removeAdmin();
        User old = new User("admin", encoder.encode("admin123"), "Administrator");
        old.setRole("ADMIN");
        users.save(old);

        new DemoDataSeeder(users, memories, encoder, "").run(null);

        User after = users.findByUsername("admin").orElseThrow();
        assertThat(encoder.matches("admin123", after.getPassword())).isFalse();
        assertThat(after.getRole()).isEqualTo("USER");
    }

    @Test
    void anOperatorSuppliedPasswordCreatesTheAdmin() {
        removeAdmin();
        new DemoDataSeeder(users, memories, encoder, "a-long-test-password-1").run(null);
        User admin = users.findByUsername("admin").orElseThrow();
        assertThat(admin.getRole()).isEqualTo("ADMIN");
        assertThat(encoder.matches("a-long-test-password-1", admin.getPassword())).isTrue();
        removeAdmin();
    }

    @Test
    void aShortOperatorPasswordIsRefused() {
        removeAdmin();
        new DemoDataSeeder(users, memories, encoder, "short").run(null);
        assertThat(users.findByUsername("admin")).isEmpty();
    }
}
