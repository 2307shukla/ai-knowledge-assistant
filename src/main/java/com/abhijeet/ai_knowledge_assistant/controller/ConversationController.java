package com.abhijeet.ai_knowledge_assistant.controller;

import com.abhijeet.ai_knowledge_assistant.domain.ChatMessage;
import com.abhijeet.ai_knowledge_assistant.domain.Conversation;
import com.abhijeet.ai_knowledge_assistant.domain.User;
import com.abhijeet.ai_knowledge_assistant.repository.ChatMessageRepository;
import com.abhijeet.ai_knowledge_assistant.repository.ConversationRepository;
import com.abhijeet.ai_knowledge_assistant.repository.UserRepository;
import com.abhijeet.ai_knowledge_assistant.service.ChatPersistence;
import com.abhijeet.ai_knowledge_assistant.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private static final int MAX_MESSAGE_LENGTH = 4000;
    private static final long STREAM_TIMEOUT_MS = 180_000L;

    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final UserRepository users;
    private final ChatService chatService;
    private final ChatPersistence persistence;
    private final ObjectMapper objectMapper;
    private final TaskExecutor taskExecutor;

    public ConversationController(ConversationRepository conversations,
                                  ChatMessageRepository messages,
                                  UserRepository users,
                                  ChatService chatService,
                                  ChatPersistence persistence,
                                  ObjectMapper objectMapper,
                                  TaskExecutor taskExecutor) {
        this.conversations = conversations;
        this.messages = messages;
        this.users = users;
        this.chatService = chatService;
        this.persistence = persistence;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
    }

    private User currentUser(Authentication authentication) {
        return users.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user not found: " + authentication.getName()));
    }

    @GetMapping
    public List<Map<String, Object>> list(Authentication authentication) {
        return conversations.findByUserOrderByUpdatedAtDesc(currentUser(authentication)).stream()
                .map(conversation -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", conversation.getId());
                    row.put("title", conversation.getTitle());
                    row.put("updatedAt", conversation.getUpdatedAt().toString());
                    return row;
                })
                .toList();
    }

    @PostMapping
    public Map<String, Object> create(Authentication authentication) {
        Conversation conversation = conversations.save(new Conversation(currentUser(authentication)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", conversation.getId());
        body.put("title", conversation.getTitle());
        return body;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id,
                                                      Authentication authentication) {
        Conversation conversation = conversations
                .findByIdAndUser(id, currentUser(authentication))
                .orElse(null);

        if (conversation == null) {
            return ResponseEntity.notFound().build();
        }

        messages.deleteAll(messages.findByConversationOrderByIdAsc(conversation));
        conversations.delete(conversation);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<Map<String, Object>>> history(@PathVariable Long id,
                                                             Authentication authentication) {
        Conversation conversation = conversations
                .findByIdAndUser(id, currentUser(authentication))
                .orElse(null);

        if (conversation == null) {
            return ResponseEntity.notFound().build();
        }

        List<Map<String, Object>> history = messages
                .findByConversationOrderByIdAsc(conversation).stream()
                .map(message -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("role", message.getRole());
                    row.put("content", message.getContent());
                    return row;
                })
                .toList();

        return ResponseEntity.ok(history);
    }

    public record MessageRequest(String content) {
    }

    /** Non-streaming send. Kept for simple clients; the UI uses the stream endpoint. */
    @PostMapping("/{id}/messages")
    public ResponseEntity<Map<String, Object>> send(@PathVariable Long id,
                                                    @RequestBody MessageRequest request,
                                                    Authentication authentication) {
        User user = currentUser(authentication);
        Conversation conversation = conversations.findByIdAndUser(id, user).orElse(null);

        if (conversation == null) {
            return ResponseEntity.notFound().build();
        }

        String text = validate(request);

        messages.save(new ChatMessage(conversation, "user", text));
        String answer = chatService.reply(user, conversation);
        messages.save(new ChatMessage(conversation, "assistant", answer));

        if ("New chat".equals(conversation.getTitle())) {
            conversation.setTitle(text.length() > 60 ? text.substring(0, 60) + "..." : text);
        }
        conversation.touch();
        conversations.save(conversation);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reply", answer);
        body.put("title", conversation.getTitle());
        return ResponseEntity.ok(body);
    }

    /**
     * Streaming send: one SSE event per token fragment, so the answer types
     * out live instead of appearing after a long pause.
     */
    @PostMapping(value = "/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long id,
                             @RequestBody MessageRequest request,
                             Authentication authentication) {

        User user = currentUser(authentication);
        Conversation conversation = conversations.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        String text = validate(request);

        final Long conversationId = conversation.getId();
        persistence.append(conversationId, "user", text, text);

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);

        taskExecutor.execute(() -> {
            try {
                String full = chatService.streamReply(user, conversationId, chunk -> {
                    try {
                        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(chunk)));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
                persistence.append(conversationId, "assistant", full, null);
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().data(
                            objectMapper.writeValueAsString(Map.of("error", String.valueOf(e.getMessage())))));
                } catch (Exception ignored) {
                    // client disconnected - nothing useful left to do
                }
                emitter.complete();
            }
        });

        return emitter;
    }

    private String validate(MessageRequest request) {
        String text = request.content() == null ? "" : request.content().trim();
        if (text.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is empty.");
        }
        if (text.length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Message too long (max " + MAX_MESSAGE_LENGTH + " characters).");
        }
        return text;
    }
}
