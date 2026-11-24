package com.abhijeet.ai_knowledge_assistant;

import com.abhijeet.ai_knowledge_assistant.domain.User;
import com.abhijeet.ai_knowledge_assistant.repository.UserRepository;
import com.abhijeet.ai_knowledge_assistant.service.RagService;
import com.abhijeet.ai_knowledge_assistant.support.FakeAiConfig;
import com.abhijeet.ai_knowledge_assistant.support.TestPdfs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Repeated requests are harmless, and a failing AI provider never leaks its message. */
@SpringBootTest(properties = "app.vector-store.path=target/test-vectors/resilience-${random.uuid}.json")
@AutoConfigureMockMvc
@Import(FakeAiConfig.class)
class ResilienceIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private RagService rag;
    @Autowired
    private FakeAiConfig.RecordingChatModel chat;

    private final JsonMapper json = JsonMapper.builder().build();

    @AfterEach
    void reset() {
        chat.failWith = null;
    }

    private String newUser() {
        String name = "r-" + UUID.randomUUID().toString().substring(0, 8);
        users.save(new User(name, "{noop}unused", name));
        return name;
    }

    private String upload(String username, String text) throws Exception {
        String body = mvc.perform(multipart("/api/rag/upload")
                        .file(new MockMultipartFile("file", "doc.pdf", "application/pdf", TestPdfs.pdf(text)))
                        .with(user(username).roles("USER")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asText();
    }

    @Test
    void deletingTwiceIsA204ThenA404NeverA500() throws Exception {
        String u = newUser();
        String id = upload(u, "Quarterly onboarding checklist for new engineers.");
        mvc.perform(delete("/api/rag/documents/" + id).with(user(u).roles("USER"))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/rag/documents/" + id).with(user(u).roles("USER"))).andExpect(status().isNotFound());
    }

    @Test
    void reindexingTwiceNeverDuplicatesPassages() throws Exception {
        String u = newUser();
        String id = upload(u, "Quarterly onboarding checklist for new engineers.");
        int before = (int) rag.search("onboarding checklist", u, 50).stream().filter(h -> h.documentId().equals(id)).count();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/rag/documents/" + id + "/reindex").with(user(u).roles("USER")))
                    .andExpect(status().isOk());
        }
        int after = (int) rag.search("onboarding checklist", u, 50).stream().filter(h -> h.documentId().equals(id)).count();
        assertThat(before).isGreaterThan(0);
        assertThat(after).isEqualTo(before);
    }

    @Test
    void aFailingProviderGivesAFriendlyMessageWithARequestIdAndNoSecret() throws Exception {
        String u = newUser();
        upload(u, "Quarterly onboarding checklist for new engineers.");
        chat.failWith = new IllegalStateException("HTTP 401 Unauthorized invalid api key sk-LEAKY-KEY-123");
        mvc.perform(get("/api/rag/ask").param("question", "onboarding checklist").with(user(u).roles("USER")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error", notNullValue()))
                .andExpect(jsonPath("$.requestId", notNullValue()))
                .andExpect(content().string(not(containsString("sk-LEAKY"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void aBusyProviderIsReportedAsTryAgainLater() throws Exception {
        String u = newUser();
        upload(u, "Quarterly onboarding checklist for new engineers.");
        chat.failWith = new IllegalStateException("429 rate limit exceeded");
        mvc.perform(get("/api/rag/ask").param("question", "onboarding checklist").with(user(u).roles("USER")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error", containsString("try again")));
    }
}
