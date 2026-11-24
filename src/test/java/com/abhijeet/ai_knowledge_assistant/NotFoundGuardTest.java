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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A weak match is not evidence: with a relevance floor, an unrelated question never reaches the model. */
@SpringBootTest(properties = {"app.vector-store.path=target/test-vectors/guard-${random.uuid}.json",
        "app.rag.min-score=0.5"})
@AutoConfigureMockMvc
@Import(FakeAiConfig.class)
class NotFoundGuardTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private FakeAiConfig.RecordingChatModel chat;

    private final JsonMapper json = JsonMapper.builder().build();

    private String userWithDocument() throws Exception {
        String name = "g-" + UUID.randomUUID().toString().substring(0, 8);
        users.save(new User(name, "{noop}unused", name));
        mvc.perform(multipart("/api/rag/upload")
                .file(new MockMultipartFile("file", "doc.pdf", "application/pdf",
                        TestPdfs.pdf("Lighthouses guide ships safely past rocks using a rotating lamp.")))
                .with(user(name).roles("USER"))).andExpect(status().isCreated());
        return name;
    }

    @Test
    void unrelatedQuestionGetsNotFoundWithoutCallingTheModel() throws Exception {
        String u = userWithDocument();
        int calls = chat.prompts.size();
        JsonNode body = json.readTree(mvc.perform(get("/api/rag/ask").param("question", "cryptocurrency mining hardware")
                        .with(user(u).roles("USER"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(body.get("found").asBoolean()).isFalse();
        assertThat(body.get("message").asText()).contains("couldn't find this in your documents");
        assertThat(body.get("sources")).isEmpty();
        assertThat(chat.prompts.size()).isEqualTo(calls);
    }

    @Test
    void relatedQuestionStillAnswers() throws Exception {
        String u = userWithDocument();
        JsonNode body = json.readTree(mvc.perform(get("/api/rag/ask")
                        .param("question", "Lighthouses guide ships safely past rocks using a rotating lamp")
                        .with(user(u).roles("USER"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(body.get("found").asBoolean()).isTrue();
        assertThat(body.get("sources")).isNotEmpty();
    }

    @Test
    void chatWithDocumentsOnAnUnrelatedQuestionSaysNotFoundAndSkipsTheModel() throws Exception {
        String u = userWithDocument();
        long conversation = json.readTree(mvc.perform(post("/api/conversations").with(user(u).roles("USER")))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        int calls = chat.prompts.size();
        String reply = mvc.perform(post("/api/conversations/" + conversation + "/messages").with(user(u).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"cryptocurrency mining hardware\",\"useDocuments\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(reply).get("reply").asText()).contains("couldn't find this in your documents");
        assertThat(chat.prompts.size()).isEqualTo(calls);
    }
}
