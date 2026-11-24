package com.abhijeet.ai_knowledge_assistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security boundary tests.
 *
 * Unit tests prove individual functions behave; these prove the access rules
 * themselves, which is what actually matters for an authenticated application.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void apiRejectsAnonymousCallers() throws Exception {
        mvc.perform(get("/api/conversations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminApiRejectsNormalUser() throws Exception {
        mvc.perform(get("/api/admin/stats").with(user("demo").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPageRejectsNormalUser() throws Exception {
        mvc.perform(get("/admin.html").with(user("demo").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminApiAllowsAdmin() throws Exception {
        mvc.perform(get("/api/admin/stats").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void landingPageIsPublic() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk());
    }

    @Test
    void loginPageIsPublic() throws Exception {
        mvc.perform(get("/login.html"))
                .andExpect(status().isOk());
    }
}
