package com.abhijeet.ai_knowledge_assistant;

import com.abhijeet.ai_knowledge_assistant.config.DatabaseUrlEnvironmentPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseUrlEnvironmentPostProcessorTest {

    private StandardEnvironment run(String databaseUrl, Map<String, Object> existing) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addLast(new MapPropertySource("existing", existing));
        if (databaseUrl != null) {
            env.getPropertySources().addFirst(new MapPropertySource("env", Map.of("DATABASE_URL", databaseUrl)));
        }
        new DatabaseUrlEnvironmentPostProcessor().postProcessEnvironment(env, null);
        return env;
    }

    @Test
    void neonStyleUrlIsSplitIntoJdbcUrlAndCredentials() {
        StandardEnvironment env = run(
                "postgresql://neondb_owner:s3cr%40t@ep-x.neon.tech/neondb?sslmode=require&channel_binding=require",
                Map.of());
        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://ep-x.neon.tech/neondb?sslmode=require&channel_binding=require");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("neondb_owner");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("s3cr@t");
    }

    @Test
    void overridesSpringDatasourceVariables() {
        StandardEnvironment env = run("postgres://u:p@host:5433/db",
                Map.of("spring.datasource.url", "jdbc:postgresql://old/db",
                        "spring.datasource.password", "stale"));
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://host:5433/db");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("p");
    }

    @Test
    void trimsWhitespaceAndQuotes() {
        StandardEnvironment env = run("  'postgresql://u:p@h/db'\n", Map.of());
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://h/db");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("p");
    }

    @Test
    void jdbcUrlPassesThrough() {
        StandardEnvironment env = run("jdbc:postgresql://h/db?user=u&password=p", Map.of());
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://h/db?user=u&password=p");
    }

    @Test
    void unsetLeavesConfigurationAlone() {
        StandardEnvironment env = run(null, Map.of("spring.datasource.url", "jdbc:h2:mem:aidb"));
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:h2:mem:aidb");
    }

    @Test
    void rejectsUnknownScheme() {
        assertThatThrownBy(() -> run("mysql://u:p@h/db", Map.of()))
                .isInstanceOf(IllegalStateException.class);
    }
}
