package com.abhijeet.ai_knowledge_assistant;

import com.abhijeet.ai_knowledge_assistant.config.SampleDocumentIndexer;
import com.abhijeet.ai_knowledge_assistant.domain.User;
import com.abhijeet.ai_knowledge_assistant.repository.UserRepository;
import com.abhijeet.ai_knowledge_assistant.service.RagService;
import com.abhijeet.ai_knowledge_assistant.support.FakeAiConfig;
import com.abhijeet.ai_knowledge_assistant.support.TestPdfs;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the privacy boundary: user B can never see or change user A's documents through any
 * path - search, chat, list, get by id, delete or re-index - and foreign ids answer 404, exactly
 * like ids that do not exist. Runs fully offline (fake AI models, in-memory database).
 */
@SpringBootTest(properties = "app.vector-store.path=target/test-vectors/privacy-${random.uuid}.json")
@AutoConfigureMockMvc
@Import(FakeAiConfig.class)
class DocumentPrivacyIntegrationTest {

    private static final String SECRET = "Zebracorn quarterly revenue forecast is confidential to Alice only.";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private RagService rag;
    @Autowired
    private FakeAiConfig.RecordingChatModel chat;

    private final JsonMapper json = JsonMapper.builder().build();
    private String alice;
    private String bob;
    private String aliceDoc;

    @BeforeEach
    void setUp() throws Exception {
        alice = newUser("alice");
        bob = newUser("bob");
        aliceDoc = upload(alice, "alice-plan.pdf", TestPdfs.pdf("Introduction page for the planning document.", SECRET))
                .get("id").asText();
    }

    @Test
    void ownerCanSeeAndOpenTheirDocument() throws Exception {
        assertThat(listIds(alice)).contains(aliceDoc);
        mvc.perform(get("/api/rag/documents/" + aliceDoc).with(as(alice))).andExpect(status().isOk());
    }

    @Test
    void otherUserDoesNotSeeTheDocumentInTheirList() throws Exception {
        assertThat(listIds(bob)).doesNotContain(aliceDoc);
    }

    @Test
    void otherUserGets404ForGetDeleteAndReindex() throws Exception {
        mvc.perform(get("/api/rag/documents/" + aliceDoc).with(as(bob))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/rag/documents/" + aliceDoc).with(as(bob))).andExpect(status().isNotFound());
        mvc.perform(post("/api/rag/documents/" + aliceDoc + "/reindex").with(as(bob))).andExpect(status().isNotFound());

        // Same answer as an id that does not exist, so ids cannot be probed.
        String missing = UUID.randomUUID().toString();
        mvc.perform(get("/api/rag/documents/" + missing).with(as(bob))).andExpect(status().isNotFound());

        // Nothing changed for the owner.
        assertThat(listIds(alice)).contains(aliceDoc);
        assertThat(searchDocumentIds(alice, "Zebracorn revenue forecast")).contains(aliceDoc);
    }

    @Test
    void searchNeverReturnsAnotherUsersPassages() throws Exception {
        assertThat(searchDocumentIds(alice, "Zebracorn revenue forecast")).contains(aliceDoc);
        assertThat(searchDocumentIds(bob, "Zebracorn revenue forecast")).doesNotContain(aliceDoc);
        assertThat(rag.search("Zebracorn revenue forecast", bob, 50))
                .noneMatch(h -> h.text().contains("Zebracorn"));
    }

    @Test
    void askEndpointCitesOnlyTheCallersDocuments() throws Exception {
        JsonNode bobAnswer = json.readTree(mvc.perform(get("/api/rag/ask").param("question", "Zebracorn revenue forecast")
                .with(as(bob))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode source : bobAnswer.path("sources")) {
            assertThat(source.path("documentId").asText()).isNotEqualTo(aliceDoc);
        }
        assertThat(bobAnswer.toString()).doesNotContain("Zebracorn");
    }

    @Test
    void chatWithDocumentsNeverSendsAnotherUsersTextToTheModel() throws Exception {
        long conversation = json.readTree(mvc.perform(post("/api/conversations").with(as(bob)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asLong();
        chat.prompts.clear();

        String body = mvc.perform(post("/api/conversations/" + conversation + "/messages").with(as(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"What is the Zebracorn revenue forecast?\",\"useDocuments\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(aliceDoc).doesNotContain("Zebracorn quarterly");
        assertThat(chat.prompts).isNotEmpty().noneMatch(p -> p.contains("confidential to Alice"));
    }

    @Test
    void ownerChatWithDocumentsGetsPageCitations() throws Exception {
        long conversation = json.readTree(mvc.perform(post("/api/conversations").with(as(alice)))
                .andReturn().getResponse().getContentAsString()).path("id").asLong();
        JsonNode reply = json.readTree(mvc.perform(post("/api/conversations/" + conversation + "/messages").with(as(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Zebracorn quarterly revenue forecast\",\"useDocuments\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode first = reply.path("sources").get(0);
        assertThat(first.path("documentId").asText()).isEqualTo(aliceDoc);
        assertThat(first.path("page").asInt()).isEqualTo(2);
        assertThat(reply.path("reply").asText()).contains("Sources:").contains("alice-plan.pdf, page 2");
    }

    @Test
    void ownerCanReindexAndDelete() throws Exception {
        mvc.perform(post("/api/rag/documents/" + aliceDoc + "/reindex").with(as(alice))).andExpect(status().isOk());
        assertThat(searchDocumentIds(alice, "Zebracorn revenue forecast")).contains(aliceDoc);

        mvc.perform(delete("/api/rag/documents/" + aliceDoc).with(as(alice))).andExpect(status().isNoContent());
        assertThat(listIds(alice)).doesNotContain(aliceDoc);
        assertThat(searchDocumentIds(alice, "Zebracorn revenue forecast")).doesNotContain(aliceDoc);
        mvc.perform(get("/api/rag/documents/" + aliceDoc).with(as(alice))).andExpect(status().isNotFound());
    }

    @Test
    void publicSampleIsReadableButNobodyCanDeleteOrReindexIt() throws Exception {
        String sample = SampleDocumentIndexer.SAMPLE_ID;
        assertThat(listIds(bob)).contains(sample);
        mvc.perform(get("/api/rag/documents/" + sample).with(as(bob))).andExpect(status().isOk());
        mvc.perform(delete("/api/rag/documents/" + sample).with(as(bob))).andExpect(status().isNotFound());
        mvc.perform(post("/api/rag/documents/" + sample + "/reindex").with(as(alice))).andExpect(status().isNotFound());
    }

    @Test
    void legacyVectorsWithoutOwnerAreQuarantined() throws Exception {
        rag.vectorStore().add(List.of(new Document(UUID.randomUUID().toString(),
                "Legacy ownerless passage about Quokkaplan budgets.", Map.of("source", "old-upload.pdf"))));
        assertThat(rag.search("Quokkaplan budgets", alice, 50)).noneMatch(h -> h.text().contains("Quokkaplan"));
        assertThat(rag.search("Quokkaplan budgets", bob, 50)).noneMatch(h -> h.text().contains("Quokkaplan"));
    }

    @Test
    void searchWithoutOwnerIsRefused() {
        assertThatThrownBy(() -> rag.search("anything", " ", 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rag.search("anything", null, 4)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anonymousCallersCannotUseTheLibrary() throws Exception {
        mvc.perform(get("/api/rag/documents")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/rag/documents/" + aliceDoc)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/rag/ask").param("question", "Zebracorn")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ helpers

    private String newUser(String prefix) {
        String name = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        users.save(new User(name, "{noop}unused", prefix));
        return name;
    }

    private static RequestPostProcessor as(String username) {
        return user(username).roles("USER");
    }

    private JsonNode upload(String username, String filename, byte[] bytes) throws Exception {
        String body = mvc.perform(multipart("/api/rag/upload")
                        .file(new MockMultipartFile("file", filename, "application/pdf", bytes))
                        .with(as(username)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private List<String> listIds(String username) throws Exception {
        JsonNode body = json.readTree(mvc.perform(get("/api/rag/documents").with(as(username)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<String> ids = new ArrayList<>();
        body.path("documents").forEach(d -> ids.add(d.path("id").asText()));
        return ids;
    }

    private List<String> searchDocumentIds(String username, String question) {
        return rag.search(question, username, 50).stream().map(h -> h.documentId()).toList();
    }
}
