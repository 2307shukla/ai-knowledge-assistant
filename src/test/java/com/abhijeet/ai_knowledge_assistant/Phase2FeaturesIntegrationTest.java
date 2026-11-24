package com.abhijeet.ai_knowledge_assistant;

import com.abhijeet.ai_knowledge_assistant.domain.User;
import com.abhijeet.ai_knowledge_assistant.repository.UserRepository;
import com.abhijeet.ai_knowledge_assistant.support.FakeAiConfig;
import com.abhijeet.ai_knowledge_assistant.support.TestPdfs;
import org.junit.jupiter.api.AfterEach;
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

/** Scoped chat, summaries with suggested questions, and Markdown export, each with owner isolation. */
@SpringBootTest(properties = "app.vector-store.path=target/test-vectors/phase2-${random.uuid}.json")
@AutoConfigureMockMvc
@Import(FakeAiConfig.class)
class Phase2FeaturesIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private FakeAiConfig.RecordingChatModel chat;

    private final JsonMapper json = JsonMapper.builder().build();

    @AfterEach
    void reset() {
        chat.reply = "Answer [1]";
    }

    private String newUser() {
        String name = "p2-" + UUID.randomUUID().toString().substring(0, 8);
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

    private JsonNode ask(String username, String question, String documents) throws Exception {
        var req = get("/api/rag/ask").param("question", question).with(user(username).roles("USER"));
        if (documents != null) {
            req = req.param("documents", documents);
        }
        return json.readTree(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void askLimitedToOneDocumentOnlyCitesThatDocument() throws Exception {
        String u = newUser();
        String apples = upload(u, "Apples grow on trees in orchards and apples are picked in autumn.");
        String boats = upload(u, "Boats float on water and boats are built in shipyards by the harbour.");

        JsonNode scoped = ask(u, "apples and boats orchards shipyards", apples);
        assertThat(scoped.get("sources")).isNotEmpty();
        scoped.get("sources").forEach(s -> assertThat(s.get("documentId").asText()).isEqualTo(apples));

        JsonNode other = ask(u, "apples and boats orchards shipyards", boats);
        other.get("sources").forEach(s -> assertThat(s.get("documentId").asText()).isEqualTo(boats));
    }

    @Test
    void askLimitedToSeveralDocumentsMayCiteEither() throws Exception {
        String u = newUser();
        String a = upload(u, "Zebra stripes camouflage zebra herds on the savanna.");
        String b = upload(u, "Zebra crossings keep pedestrians safe on busy city roads.");
        JsonNode both = ask(u, "zebra", a + "," + b);
        assertThat(both.get("sources").size()).isGreaterThanOrEqualTo(2);
        both.get("sources").forEach(s -> assertThat(s.get("documentId").asText()).isIn(a, b));
    }

    @Test
    void anotherUsersDocumentIdAnswers404AndNothingReachesTheModel() throws Exception {
        String owner = newUser();
        String intruder = newUser();
        String secret = upload(owner, "The vault combination discussion is strictly private.");
        int calls = chat.prompts.size();

        mvc.perform(get("/api/rag/ask").param("question", "vault combination").param("documents", secret)
                .with(user(intruder).roles("USER"))).andExpect(status().isNotFound());
        // A mixed list fails as a whole: one foreign id must not be silently dropped.
        String mine = upload(intruder, "My own notes about gardening: tomatoes need sun, water and regular feeding in summer.");
        mvc.perform(get("/api/rag/ask").param("question", "vault").param("documents", mine + "," + secret)
                .with(user(intruder).roles("USER"))).andExpect(status().isNotFound());
        assertThat(chat.prompts.size()).isEqualTo(calls);
    }

    @Test
    void tooManyDocumentsInOneScopeIsRejected() throws Exception {
        String u = newUser();
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < 21; i++) {
            ids.append(i == 0 ? "" : ",").append(UUID.randomUUID());
        }
        mvc.perform(get("/api/rag/ask").param("question", "anything").param("documents", ids.toString())
                .with(user(u).roles("USER"))).andExpect(status().isBadRequest());
    }

    @Test
    void scopedConversationCitesOnlyTheChosenDocumentAndRefusesForeignOnes() throws Exception {
        String owner = newUser();
        String other = newUser();
        String mine = upload(owner, "Quarterly onboarding checklist for new engineers.");
        String theirs = upload(other, "Quarterly onboarding checklist, the other team's copy.");

        long conversation = json.readTree(mvc.perform(post("/api/conversations").with(user(owner).roles("USER")))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        String ok = mvc.perform(post("/api/conversations/" + conversation + "/messages").with(user(owner).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"onboarding checklist\",\"useDocuments\":true,\"documentIds\":[\"" + mine + "\"]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        json.readTree(ok).get("sources").forEach(s -> assertThat(s.get("documentId").asText()).isEqualTo(mine));

        mvc.perform(post("/api/conversations/" + conversation + "/messages").with(user(owner).roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"onboarding checklist\",\"useDocuments\":true,\"documentIds\":[\"" + theirs + "\"]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void summaryReturnsTextAndThreeQuestionsAndIsCached() throws Exception {
        String u = newUser();
        String id = upload(u, "Photosynthesis lets plants turn light, water and carbon dioxide into sugar.");
        chat.reply = "Plants make sugar from light.\nIt happens in leaves.\nQ: What is photosynthesis?\nQ: Where does it happen?\nQ: What does it need?\nQ: A fourth one";
        int before = chat.prompts.size();

        JsonNode first = json.readTree(mvc.perform(get("/api/rag/documents/" + id + "/summary").with(user(u).roles("USER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(first.get("summary").asText()).isEqualTo("Plants make sugar from light. It happens in leaves.");
        assertThat(first.get("questions")).hasSize(3);
        assertThat(first.get("questions").get(0).asText()).isEqualTo("What is photosynthesis?");

        mvc.perform(get("/api/rag/documents/" + id + "/summary").with(user(u).roles("USER"))).andExpect(status().isOk());
        assertThat(chat.prompts.size()).isEqualTo(before + 1);
    }

    @Test
    void summaryOfAnotherUsersDocumentIs404() throws Exception {
        String owner = newUser();
        String intruder = newUser();
        String id = upload(owner, "Private board minutes about acquisitions.");
        int before = chat.prompts.size();
        mvc.perform(get("/api/rag/documents/" + id + "/summary").with(user(intruder).roles("USER")))
                .andExpect(status().isNotFound());
        assertThat(chat.prompts.size()).isEqualTo(before);
    }

    @Test
    void markdownExportContainsTheConversationForItsOwnerOnly() throws Exception {
        String owner = newUser();
        String other = newUser();
        long conversation = json.readTree(mvc.perform(post("/api/conversations").with(user(owner).roles("USER")))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/conversations/" + conversation + "/messages").with(user(owner).roles("USER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"What is a monad?\"}"))
                .andExpect(status().isOk());

        var result = mvc.perform(get("/api/conversations/" + conversation + "/export.md").with(user(owner).roles("USER")))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(result.getContentType()).startsWith("text/markdown");
        assertThat(result.getHeader("Content-Disposition")).contains("conversation-" + conversation + ".md");
        String body = result.getContentAsString();
        assertThat(body).startsWith("# ").contains("## You").contains("What is a monad?").contains("## Assistant");

        mvc.perform(get("/api/conversations/" + conversation + "/export.md").with(user(other).roles("USER")))
                .andExpect(status().isNotFound());
    }
}
