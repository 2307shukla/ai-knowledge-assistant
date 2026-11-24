package com.abhijeet.ai_knowledge_assistant;

import com.abhijeet.ai_knowledge_assistant.domain.User;
import com.abhijeet.ai_knowledge_assistant.repository.UserRepository;
import com.abhijeet.ai_knowledge_assistant.support.FakeAiConfig;
import com.abhijeet.ai_knowledge_assistant.support.TestPdfs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Upload limits (size, type by content, pages, quota), filename sanitising and the error format.
 * Limits are set low here so the tests stay small.
 */
@SpringBootTest(properties = {
        "app.vector-store.path=target/test-vectors/limits-${random.uuid}.json",
        "app.documents.max-bytes=40000",
        "app.documents.max-pages=3",
        "app.documents.max-per-user=2"})
@AutoConfigureMockMvc
@Import(FakeAiConfig.class)
class UploadLimitsIntegrationTest {

    private static final String TEXT = "This page explains the onboarding checklist for new engineers in detail.";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;

    @Test
    void acceptsAValidPdf() throws Exception {
        upload(newUser(), "guide.pdf", TestPdfs.pdf(TEXT, TEXT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pages").value(2))
                .andExpect(jsonPath("$.type").value("pdf"));
    }

    @Test
    void rejectsATextFileRenamedToPdf() throws Exception {
        expectError(upload(newUser(), "notes.pdf", "plain text, not a PDF".getBytes(StandardCharsets.UTF_8)), 415);
    }

    @Test
    void rejectsAnExecutableEvenWithPdfContentType() throws Exception {
        byte[] exe = new byte[2048];
        exe[0] = 'M';
        exe[1] = 'Z';
        expectError(upload(newUser(), "invoice.pdf", exe), 415);
    }

    @Test
    void rejectsAnEmptyFile() throws Exception {
        expectError(upload(newUser(), "empty.pdf", new byte[0]), 400);
    }

    @Test
    void rejectsAFileOverTheSizeLimit() throws Exception {
        byte[] big = new byte[50_000];
        Arrays.fill(big, (byte) ' ');
        System.arraycopy("%PDF-1.7".getBytes(StandardCharsets.US_ASCII), 0, big, 0, 8);
        expectError(upload(newUser(), "big.pdf", big), 413);
    }

    @Test
    void rejectsTooManyPages() throws Exception {
        expectError(upload(newUser(), "long.pdf", TestPdfs.pdf(TEXT, TEXT, TEXT, TEXT)), 413);
    }

    @Test
    void rejectsADamagedPdf() throws Exception {
        expectError(upload(newUser(), "broken.pdf", "%PDF-1.7\nthis is not really a pdf".getBytes(StandardCharsets.US_ASCII)), 422);
    }

    @Test
    void rejectsAPasswordProtectedPdf() throws Exception {
        expectError(upload(newUser(), "locked.pdf", TestPdfs.encryptedPdf(TEXT)), 422);
    }

    @Test
    void rejectsAPdfWithNoText() throws Exception {
        expectError(upload(newUser(), "blank.pdf", TestPdfs.pdf("")), 422);
    }

    @Test
    void enforcesThePerUserQuota() throws Exception {
        String u = newUser();
        upload(u, "a.pdf", TestPdfs.pdf(TEXT)).andExpect(status().isCreated());
        upload(u, "b.pdf", TestPdfs.pdf(TEXT)).andExpect(status().isCreated());
        expectError(upload(u, "c.pdf", TestPdfs.pdf(TEXT)), 409);
    }

    @Test
    void stripsPathsFromFilenames() throws Exception {
        upload(newUser(), "../../etc/<script>passwd.pdf", TestPdfs.pdf(TEXT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.file").value("scriptpasswd.pdf"));
    }

    @Test
    void missingFilePartGivesACleanError() throws Exception {
        expectError(mvc.perform(multipart("/api/rag/upload").with(user(newUser()).roles("USER"))), 400);
    }

    @Test
    void errorsNeverLeakInternals() throws Exception {
        mvc.perform(get("/api/rag/ask").with(user(newUser()).roles("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.requestId", notNullValue()))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("at com."))));
    }

    @Test
    void livenessProbeIsPublicAndCheap() throws Exception {
        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("UP")));
    }

    @Test
    void everyResponseCarriesARequestId() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(header().exists("X-Request-Id"));
        mvc.perform(get("/actuator/health/liveness").header("X-Request-Id", "bad id with spaces"))
                .andExpect(header().string("X-Request-Id", not(containsString(" "))));
    }

    @Test
    void forgedHeaderWithNewlineIsABadRequestNotAServerError() throws Exception {
        // Reproduces the case seen in CI: the firewall rejects the header when the handler reads it.
        expectError(mvc.perform(get("/actuator/health/liveness")
                .header("X-Request-Id", "bad id\nforged-log-line")), 400);
    }

    // ------------------------------------------------------------------ helpers

    private String newUser() {
        String name = "u-" + UUID.randomUUID().toString().substring(0, 8);
        users.save(new User(name, "{noop}unused", name));
        return name;
    }

    private ResultActions upload(String username, String filename, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/rag/upload")
                .file(new MockMultipartFile("file", filename, "application/pdf", bytes))
                .with(user(username).roles("USER")));
    }

    /** Every error: the expected status, a readable message, a request id and no internals. */
    private static void expectError(ResultActions result, int expected) throws Exception {
        result.andExpect(status().is(expected))
                .andExpect(jsonPath("$.status").value(expected))
                .andExpect(jsonPath("$.error", notNullValue()))
                .andExpect(jsonPath("$.requestId", notNullValue()))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("org.apache"))));
    }
}
