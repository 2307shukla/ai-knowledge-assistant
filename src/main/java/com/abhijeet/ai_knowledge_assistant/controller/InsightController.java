package com.abhijeet.ai_knowledge_assistant.controller;

import com.abhijeet.ai_knowledge_assistant.util.InputSanitizer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Structured output: the model is asked to return a typed object, and Spring AI
 * converts the JSON into a record via a generated format instruction.
 *
 * If the model returns anything unparseable this throws rather than guessing -
 * a deliberate choice, because silently returning a half-parsed object is worse
 * than a clear error.
 */
@RestController
@RequestMapping("/api/insights")
public class InsightController {

    private final ChatClient chatClient;

    public InsightController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public record QuestionRequest(String question) {
    }

    public record Insight(
            String summary,
            List<String> keyPoints,
            String confidence
    ) {
    }

    @PostMapping
    public Insight analyse(@RequestBody QuestionRequest request) {
        String question = InputSanitizer.clean(request.question(), 500);
        if (question.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question is empty.");
        }

        Insight insight = chatClient.prompt()
                .system("""
                        You are a concise analyst.
                        Answer the user's question and fill the requested structure exactly.
                        confidence must be one of: low, medium, high.
                        Keep summary under 40 words and give at most 5 key points.
                        """)
                .user(question)
                .call()
                .entity(Insight.class);

        // A weak model can emit JSON that parses but carries no values. Returning
        // that as a 200 hides a real failure, so surface it instead.
        if (insight == null
                || (insight.summary() == null && insight.keyPoints() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The model did not return the requested structure. "
                            + "This usually means the model is too small for reliable JSON output.");
        }

        return insight;
    }
}
