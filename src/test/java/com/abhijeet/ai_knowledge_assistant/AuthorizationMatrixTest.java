package com.abhijeet.ai_knowledge_assistant;

import com.abhijeet.ai_knowledge_assistant.domain.User;
import com.abhijeet.ai_knowledge_assistant.repository.UserRepository;
import com.abhijeet.ai_knowledge_assistant.support.FakeAiConfig;
import com.abhijeet.ai_knowledge_assistant.support.TestPdfs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The user A -> user B matrix: every endpoint that takes a document id answers 404 (not 403) for
 * someone else's document, never reveals its text, and leaves it untouched.
 */
@SpringBootTest(properties = "app.vector-store.path=target/test-vectors/matrix-${random.uuid}.json")
@AutoConfigureMockMvc
@Import(FakeAiConfig.class)
class AuthorizationMatrixTest {

    private static final String SECRET = "Marmalade-Protocol-9917 is the secret launch codename.";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;

    private final JsonMapper json = JsonMapper.builder().build();

    private RequestPostProcessor newUser() {
        String name = "m-" + UUID.randomUUID().toString().substring(0, 8);
        users.save(new User(name, "{noop}unused", name));
        return user(name).roles("USER");
    }

    @Test
    void everyDocumentEndpointAnswers404ForAnotherUsersDocument() throws Exception {
        RequestPostProcessor a = newUser();
        RequestPostProcessor b = newUser();
        String id = json.readTree(mvc.perform(multipart("/api/rag/upload")
                        .file(new MockMultipartFile("file", "plan.pdf", "application/pdf", TestPdfs.pdf(SECRET))).with(a))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        String base = "/api/rag/documents/" + id;

        mvc.perform(get(base).with(b)).andExpect(status().isNotFound());
        mvc.perform(get(base + "/passages").with(b)).andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Marmalade"))));
        mvc.perform(get(base + "/summary").with(b)).andExpect(status().isNotFound());
        mvc.perform(post(base + "/reindex").with(b)).andExpect(status().isNotFound());
        mvc.perform(delete(base).with(b)).andExpect(status().isNotFound());
        mvc.perform(get("/api/rag/ask").param("question", "Marmalade").param("documents", id).with(b))
                .andExpect(status().isNotFound());
        long conversation = json.readTree(mvc.perform(post("/api/conversations").with(b))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/conversations/" + conversation + "/messages").with(b)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Marmalade\",\"useDocuments\":true,\"documentIds\":[\"" + id + "\"]}"))
                .andExpect(status().isNotFound());

        // B's attempts changed nothing: the owner still has the document and can read its page.
        mvc.perform(get(base).with(a)).andExpect(status().isOk());
        JsonNode page = json.readTree(mvc.perform(get(base + "/passages").param("page", "1").with(a))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(page.get("passages")).isNotEmpty();
        assertThat(page.get("passages").get(0).get("text").asText()).contains("Marmalade-Protocol-9917");

        // And B's own list and unscoped questions never surface it.
        mvc.perform(get("/api/rag/documents").with(b)).andExpect(content().string(not(containsString(id))));
        mvc.perform(get("/api/rag/ask").param("question", "Marmalade-Protocol-9917").with(b))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("Marmalade"))))
                .andExpect(jsonPath("$.sources").isArray());
    }

    @Test
    void passagesCanBeFilteredByPage() throws Exception {
        RequestPostProcessor a = newUser();
        String id = json.readTree(mvc.perform(multipart("/api/rag/upload")
                        .file(new MockMultipartFile("file", "p.pdf", "application/pdf",
                                TestPdfs.pdf("A page of ordinary text that is long enough to be indexed properly.")))
                        .with(a)).andReturn().getResponse().getContentAsString()).get("id").asText();
        JsonNode none = json.readTree(mvc.perform(get("/api/rag/documents/" + id + "/passages").param("page", "99").with(a))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(none.get("passages")).isEmpty();
    }
}
