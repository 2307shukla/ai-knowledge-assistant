package com.abhijeet.ai_knowledge_assistant.controller;

import com.abhijeet.ai_knowledge_assistant.domain.ChatMessage;
import com.abhijeet.ai_knowledge_assistant.domain.Conversation;
import com.abhijeet.ai_knowledge_assistant.domain.User;
import com.abhijeet.ai_knowledge_assistant.repository.ChatMessageRepository;
import com.abhijeet.ai_knowledge_assistant.repository.ConversationRepository;
import com.abhijeet.ai_knowledge_assistant.repository.UserRepository;
import com.abhijeet.ai_knowledge_assistant.export.SimplePdf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Exports a conversation as CSV (data) or PDF (sharing). */
@RestController
@RequestMapping("/api/conversations")
public class ExportController {

    private static final Logger log = LoggerFactory.getLogger(ExportController.class);

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final UserRepository users;

    public ExportController(ConversationRepository conversations,
                            ChatMessageRepository messages,
                            UserRepository users) {
        this.conversations = conversations;
        this.messages = messages;
        this.users = users;
    }

    private Conversation owned(Long id, Authentication authentication) {
        User user = users.findByUsername(authentication.getName()).orElseThrow();
        return conversations.findByIdAndUser(id, user).orElse(null);
    }

    @GetMapping("/{id}/export.csv")
    public ResponseEntity<byte[]> csv(@PathVariable Long id, Authentication authentication) {
        Conversation conversation = owned(id, authentication);
        if (conversation == null) {
            return ResponseEntity.notFound().build();
        }

        StringBuilder sb = new StringBuilder("role,timestamp,content\n");
        for (ChatMessage message : messages.findByConversationOrderByIdAsc(conversation)) {
            sb.append(message.getRole()).append(',')
                    .append(STAMP.format(message.getCreatedAt())).append(',')
                    .append('"').append(csvSafe(message.getContent()).replace("\"", "\"\"")).append('"')
                    .append('\n');
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"conversation-" + id + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/{id}/export.pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id, Authentication authentication) {
        Conversation conversation = owned(id, authentication);
        if (conversation == null) {
            return ResponseEntity.notFound().build();
        }

        List<ChatMessage> history = messages.findByConversationOrderByIdAsc(conversation);

        try (SimplePdf pdf = new SimplePdf()) {
            pdf.paragraph(conversation.getTitle(), SimplePdf.Style.TITLE)
                    .paragraph("Exported " + STAMP.format(Instant.now()) + " \u00b7 " + history.size() + " messages",
                            SimplePdf.Style.META);
            for (ChatMessage message : history) {
                pdf.paragraph(message.getRole().toUpperCase(java.util.Locale.ROOT) + " \u00b7 "
                        + STAMP.format(message.getCreatedAt()), SimplePdf.Style.META)
                        .paragraph(message.getContent(), SimplePdf.Style.BODY);
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"conversation-" + id + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf.toBytes());
        } catch (IOException e) {
            log.error("PDF export failed for conversation id={}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /** Stops spreadsheet apps from running a cell as a formula (CSV injection). */
    static String csvSafe(String value) {
        if (value != null && !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value == null ? "" : value;
    }
}
