package com.abhijeet.ai_knowledge_assistant.controller;

import com.abhijeet.ai_knowledge_assistant.domain.ChatMessage;
import com.abhijeet.ai_knowledge_assistant.domain.Conversation;
import com.abhijeet.ai_knowledge_assistant.domain.User;
import com.abhijeet.ai_knowledge_assistant.repository.ChatMessageRepository;
import com.abhijeet.ai_knowledge_assistant.repository.ConversationRepository;
import com.abhijeet.ai_knowledge_assistant.repository.UserRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Exports a conversation as CSV (data) or PDF (sharing). */
@RestController
@RequestMapping("/api/conversations")
public class ExportController {

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
                    .append('"').append(message.getContent().replace("\"", "\"\"")).append('"')
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

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();

            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font meta = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 11);

            document.add(new Paragraph(conversation.getTitle(), title));
            document.add(new Paragraph(
                    "Exported " + STAMP.format(java.time.Instant.now()) + " · "
                            + history.size() + " messages", meta));
            document.add(new Paragraph(" "));

            for (ChatMessage message : history) {
                document.add(new Paragraph(
                        message.getRole().toUpperCase() + " · "
                                + STAMP.format(message.getCreatedAt()), meta));
                document.add(new Paragraph(message.getContent(), body));
                document.add(new Paragraph(" "));
            }

            document.close();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"conversation-" + id + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(out.toByteArray());

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
