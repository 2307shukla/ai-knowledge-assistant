package com.abhijeet.ai_knowledge_assistant.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lets DATABASE_URL hold a connection string exactly as Neon, Render or Supabase
 * show it (postgresql://user:password@host/db?sslmode=require) and turns it into
 * spring.datasource.url / username / password.
 *
 * When DATABASE_URL is set it takes precedence over SPRING_DATASOURCE_*, so a
 * single copied value is the whole database configuration.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String SOURCE_NAME = "databaseUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String raw = environment.getProperty("DATABASE_URL");
        if (raw == null || raw.isBlank()) {
            return;
        }
        environment.getPropertySources().addFirst(
                new MapPropertySource(SOURCE_NAME, toDatasourceProperties(raw)));
    }

    static Map<String, Object> toDatasourceProperties(String raw) {
        String value = stripQuotes(raw.trim());
        Map<String, Object> props = new LinkedHashMap<>();

        if (value.startsWith("jdbc:")) {
            props.put("spring.datasource.url", value);
            return props;
        }
        if (!value.startsWith("postgres://") && !value.startsWith("postgresql://")) {
            throw new IllegalStateException(
                    "DATABASE_URL must start with postgresql://, postgres:// or jdbc:");
        }

        URI uri = URI.create(value);
        if (uri.getHost() == null) {
            throw new IllegalStateException("DATABASE_URL has no host");
        }

        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
        if (uri.getPort() != -1) {
            jdbc.append(':').append(uri.getPort());
        }
        String path = uri.getRawPath();
        jdbc.append(path == null || path.isEmpty() ? "/" : path);
        if (uri.getRawQuery() != null) {
            jdbc.append('?').append(uri.getRawQuery());
        }
        props.put("spring.datasource.url", jdbc.toString());

        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            String user = colon >= 0 ? userInfo.substring(0, colon) : userInfo;
            props.put("spring.datasource.username", decode(user));
            if (colon >= 0) {
                props.put("spring.datasource.password", decode(userInfo.substring(colon + 1)));
            }
        }
        return props;
    }

    private static String stripQuotes(String s) {
        boolean quoted = s.length() >= 2
                && ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\"")));
        return quoted ? s.substring(1, s.length() - 1).trim() : s;
    }

    private static String decode(String s) {
        // URLDecoder treats '+' as a space; in a URL's user-info it is a literal plus.
        return URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
