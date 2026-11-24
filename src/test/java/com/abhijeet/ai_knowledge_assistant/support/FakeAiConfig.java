package com.abhijeet.ai_knowledge_assistant.support;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Offline stand-ins for the AI provider, so tests need no key and no network.
 * Embeddings are a bag-of-words hash (texts sharing words are close); the chat model answers
 * "Answer [1]" and records every prompt so tests can check what reached the model.
 */
@TestConfiguration
public class FakeAiConfig {

    public static final int DIMENSIONS = 768;

    @Bean
    @Primary
    public EmbeddingModel fakeEmbeddingModel() {
        return new FakeEmbeddingModel();
    }

    @Bean
    @Primary
    public RecordingChatModel fakeChatModel() {
        return new RecordingChatModel();
    }

    public static float[] vector(String text) {
        float[] v = new float[DIMENSIONS];
        for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (!word.isEmpty()) {
                v[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1f;
            }
        }
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        if (norm == 0) {
            v[0] = 1f;
            return v;
        }
        float n = (float) Math.sqrt(norm);
        for (int i = 0; i < v.length; i++) {
            v[i] /= n;
        }
        return v;
    }

    static final class FakeEmbeddingModel implements EmbeddingModel {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> out = new ArrayList<>();
            List<String> texts = request.getInstructions();
            for (int i = 0; i < texts.size(); i++) {
                out.add(new Embedding(vector(texts.get(i)), i));
            }
            return new EmbeddingResponse(out);
        }

        @Override
        public float[] embed(Document document) {
            return vector(document.getText());
        }

        @Override
        public int dimensions() {
            return DIMENSIONS;
        }
    }

    public static final class RecordingChatModel implements ChatModel {
        public final List<String> prompts = new CopyOnWriteArrayList<>();
        /** When set, every call fails with this exception (to test error handling). */
        public volatile RuntimeException failWith;
        /** What the model answers; tests change it to exercise parsing. */
        public volatile String reply = "Answer [1]";

        @Override
        public ChatResponse call(Prompt prompt) {
            if (failWith != null) {
                throw failWith;
            }
            prompts.add(prompt.getContents());
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(call(prompt));
        }
    }
}
