package com.abhijeet.ai_knowledge_assistant;

import com.abhijeet.ai_knowledge_assistant.domain.User;
import com.abhijeet.ai_knowledge_assistant.repository.UserRepository;
import com.abhijeet.ai_knowledge_assistant.support.FakeAiConfig;
import com.abhijeet.ai_knowledge_assistant.support.TestPdfs;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Logs must hold ids and counts only: no usernames, e-mail addresses, file names, document text
 * or provider messages. Two checks: a live one that drives the app and reads what it logged, and a
 * source scan that keeps risky values out of log statements in the first place.
 */
@SpringBootTest(properties = "app.vector-store.path=target/test-vectors/logs-${random.uuid}.json")
@AutoConfigureMockMvc
@Import(FakeAiConfig.class)
class LogHygieneTest {

    private static final String EMAIL_IN_FILENAME = "jane.doe@example.com";
    private static final String DOCUMENT_TEXT = "CONFIDENTIAL-PAYLOAD-7731 appears only inside the document.";
    private static final String PROVIDER_SECRET = "sk-LEAKY-KEY-123";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private FakeAiConfig.RecordingChatModel chat;

    private Capture capture;

    @BeforeEach
    void startCapture() {
        capture = new Capture();
        capture.start();
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        context.getConfiguration().getRootLogger().addAppender(capture, Level.ALL, null);
        context.updateLoggers();
    }

    @AfterEach
    void stopCapture() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        context.getConfiguration().getRootLogger().removeAppender(capture.getName());
        context.updateLoggers();
        chat.failWith = null;
    }

    @Test
    void uploadsQuestionsAndFailuresLeaveNoPersonalDataInTheLogs() throws Exception {
        String username = "redaction-user-" + UUID.randomUUID().toString().substring(0, 8);
        users.save(new User(username, "{noop}unused", "Redaction Tester"));

        mvc.perform(multipart("/api/rag/upload")
                .file(new MockMultipartFile("file", EMAIL_IN_FILENAME + "-report.pdf", "application/pdf",
                        TestPdfs.pdf(DOCUMENT_TEXT)))
                .with(user(username).roles("USER"))).andReturn();
        mvc.perform(multipart("/api/rag/upload")
                .file(new MockMultipartFile("file", EMAIL_IN_FILENAME + ".pdf", "application/pdf", "not a pdf".getBytes()))
                .with(user(username).roles("USER"))).andReturn();
        mvc.perform(get("/api/rag/ask").param("question", "CONFIDENTIAL-PAYLOAD-7731").with(user(username).roles("USER")))
                .andReturn();

        chat.failWith = new IllegalStateException("HTTP 401 invalid api key " + PROVIDER_SECRET);
        mvc.perform(get("/api/rag/ask").param("question", "anything about the payload").with(user(username).roles("USER")))
                .andReturn();
        long conversation = Long.parseLong(Pattern.compile("\"id\":(\\d+)").matcher(
                mvc.perform(post("/api/conversations").with(user(username).roles("USER")))
                        .andReturn().getResponse().getContentAsString()).results()
                .findFirst().orElseThrow().group(1));
        mvc.perform(post("/api/conversations/" + conversation + "/messages").with(user(username).roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"hello " + EMAIL_IN_FILENAME + "\",\"useDocuments\":true}")).andReturn();

        assertThat(capture.messages).isNotEmpty();
        String everything = String.join("\n", capture.messages);
        assertThat(everything)
                .doesNotContain(EMAIL_IN_FILENAME)
                .doesNotContain("CONFIDENTIAL-PAYLOAD-7731")
                .doesNotContain(username)
                .doesNotContain(PROVIDER_SECRET)
                .doesNotContain("Redaction Tester");
    }

    /** Log statements may not mention personal values or raw exception messages. */
    @Test
    void sourceCodeNeverLogsPersonalValues() throws IOException {
        Pattern statement = Pattern.compile("\\blog\\.(info|warn|error|debug|trace)\\((.*?)\\);", Pattern.DOTALL);
        Pattern forbidden = Pattern.compile(
                "getUsername\\(\\)|getName\\(\\)|\\bemail\\b|getMessage\\(\\)|getFilename\\(\\)|getOriginalFilename\\(\\)"
                        + "|getText\\(\\)|\\.content\\(\\)|getDisplayName\\(\\)|getPassword|apiKey|plain\\b|password",
                Pattern.CASE_INSENSITIVE);
        List<String> offences = new CopyOnWriteArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                Matcher m = statement.matcher(source);
                while (m.find()) {
                    String call = m.group(2);
                    Matcher bad = forbidden.matcher(call);
                    if (bad.find()) {
                        offences.add(file.getFileName() + ": " + call.replaceAll("\\s+", " ").trim());
                    }
                }
            }
        }
        assertThat(offences).as("log statements that may carry personal data").isEmpty();
    }

    private static final class Capture extends AbstractAppender {
        final List<String> messages = new CopyOnWriteArrayList<>();

        Capture() {
            super("test-capture", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            messages.add(event.getMessage().getFormattedMessage());
        }
    }
}
