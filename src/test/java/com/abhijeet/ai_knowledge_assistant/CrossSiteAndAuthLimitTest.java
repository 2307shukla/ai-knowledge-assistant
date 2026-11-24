package com.abhijeet.ai_knowledge_assistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CSRF defence (cross-site state changes are refused) and the stricter sign-in rate limit. */
@SpringBootTest(properties = "app.vector-store.path=target/test-vectors/csrf-${random.uuid}.json")
@AutoConfigureMockMvc
class CrossSiteAndAuthLimitTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void stateChangeFromAnotherOriginIsRefused() throws Exception {
        mvc.perform(post("/api/conversations").with(user("u1").roles("USER"))
                        .header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
    }

    @Test
    void stateChangeMarkedCrossSiteByTheBrowserIsRefused() throws Exception {
        mvc.perform(post("/api/conversations").with(user("u1").roles("USER"))
                        .header("Sec-Fetch-Site", "cross-site"))
                .andExpect(status().isForbidden());
    }

    @Test
    void nullOriginWithCrossSiteFetchMetadataIsRefused() throws Exception {
        mvc.perform(post("/api/conversations").with(user("u1").roles("USER"))
                        .header("Origin", "null").header("Sec-Fetch-Site", "cross-site"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sameOriginAndNonBrowserRequestsPass() throws Exception {
        mvc.perform(post("/api/conversations").with(user("u2").roles("USER")).header("Origin", "http://localhost"))
                .andExpect(status().is2xxSuccessful());
        mvc.perform(post("/api/conversations").with(user("u2").roles("USER")).header("Sec-Fetch-Site", "same-origin"))
                .andExpect(status().is2xxSuccessful());
        mvc.perform(post("/api/conversations").with(user("u2").roles("USER")))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void readsFromAnotherOriginAreNotBlockedByThisFilter() throws Exception {
        mvc.perform(get("/actuator/health/liveness").header("Origin", "https://evil.example"))
                .andExpect(status().isOk());
    }

    @Test
    void crossSiteLoginPostIsRefused() throws Exception {
        mvc.perform(post("/login").header("Origin", "https://evil.example")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "demo").param("password", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrationIsRateLimitedPerIp() throws Exception {
        int limited = 0;
        for (int i = 0; i < 14; i++) {
            int code = mvc.perform(post("/api/auth/register")
                            .with(request -> {
                                request.setRemoteAddr("203.0.113.77");
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"x\",\"password\":\"y\"}"))
                    .andReturn().getResponse().getStatus();
            if (code == 429) {
                limited++;
            }
        }
        assertThat(limited).isGreaterThanOrEqualTo(3);
    }

    @Test
    void loginAttemptsAreRateLimitedPerIp() throws Exception {
        int limited = 0;
        for (int i = 0; i < 14; i++) {
            int code = mvc.perform(post("/login")
                            .with(request -> {
                                request.setRemoteAddr("203.0.113.88");
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("username", "nobody").param("password", "wrong"))
                    .andReturn().getResponse().getStatus();
            if (code == 429) {
                limited++;
            }
        }
        assertThat(limited).isGreaterThanOrEqualTo(3);
    }
}
