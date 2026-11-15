package com.abhijeet.ai_knowledge_assistant.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lets a visitor try the assistant without creating an account, like a
 * limited guest session, then asks them to sign up.
 *
 * The counter lives in the HTTP session, so it resets when the browser session
 * ends. That is deliberate: this is a taste of the product, not a free tier.
 * A real quota needs a database keyed by user or IP.
 */
@RestController
@RequestMapping("/api/guest")
public class GuestChatController {

    private static final int FREE_MESSAGES = 5;
    private static final String COUNTER = "guestMessages";

    private final ChatModel chatModel;

    public GuestChatController(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public record GuestRequest(String message) {
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody GuestRequest request,
                                                    HttpSession session) {
        Integer used = (Integer) session.getAttribute(COUNTER);
        int count = used == null ? 0 : used;

        if (count >= FREE_MESSAGES) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "Your free trial is finished. Create an account to keep chatting.");
            body.put("signInRequired", true);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
        }

        String text = com.abhijeet.ai_knowledge_assistant.util.InputSanitizer
                .clean(request.message(), 1000);
        if (text.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message is empty."));
        }

        String reply;
        try {
            reply = chatModel.call(text);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "The AI provider failed: " + e.getMessage()));
        }

        session.setAttribute(COUNTER, count + 1);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reply", reply);
        body.put("remaining", FREE_MESSAGES - count - 1);
        return ResponseEntity.ok(body);
    }

    @org.springframework.web.bind.annotation.GetMapping("/quota")
    public Map<String, Object> quota(HttpSession session) {
        Integer used = (Integer) session.getAttribute(COUNTER);
        int count = used == null ? 0 : used;
        return Map.of("remaining", Math.max(0, FREE_MESSAGES - count));
    }
}
