package com.abhijeet.ai_knowledge_assistant.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; "
            + "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; "
            + "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; "
            + "img-src 'self' data: https:; font-src 'self' data:; connect-src 'self'; "
            + "object-src 'none'; base-uri 'self'; frame-ancestors 'none'; "
            + "form-action 'self' https://accounts.google.com";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           ClientRegistrationRepository registrations,
                                           OAuthLoginSuccessHandler oauthSuccess) throws Exception {

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/index.html", "/login.html", "/register.html",
                                "/api/auth/**", "/api/guest/**",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**",
                                "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness", "/actuator/info",
                                "/css/**", "/js/**", "/favicon.ico", "/error")
                        .permitAll()
                        .requestMatchers("/admin.html", "/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/chat.html", true)
                        .failureUrl("/login.html?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login.html?logout")
                        .permitAll())
                // API calls get a JSON 401; browser navigations get the login page.
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                new AntPathRequestMatcher("/api/**"))
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login.html"),
                                AnyRequestMatcher.INSTANCE))
                // Browser hardening. Inline scripts are still used by the pages, hence 'unsafe-inline';
                // scripts may only come from this site and the jsDelivr CDN. No other site may frame us.
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .frameOptions(frame -> frame.deny())
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                                "camera=(), geolocation=(), microphone=(self)")))
                // JSON fetch calls from a single page; CSRF disabled for this demo.
                .csrf(csrf -> csrf.disable());

        // Only wire the OAuth2 login flow when a Google client is configured.
        if (registrations.findByRegistrationId("google") != null) {
            http.oauth2Login(oauth -> oauth
                    .loginPage("/login.html")
                    .successHandler(oauthSuccess)
                    .failureUrl("/login.html?oauthError"));
        }

        return http.build();
    }
}
