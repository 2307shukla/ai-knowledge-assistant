package com.abhijeet.ai_knowledge_assistant;

import com.abhijeet.ai_knowledge_assistant.config.HttpEmbeddingModel;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The embedding client copes with a Gemini-style response that has no usage block, and fails safely. */
class HttpEmbeddingModelTest {

    private HttpServer server;
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> auth = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            calls.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            bodies.add(body);
            auth.add(exchange.getRequestHeaders().getFirst("Authorization"));
            int inputs = body.split("\"input\":\\[")[1].split("\\]")[0].split("\",\"").length;
            StringBuilder data = new StringBuilder();
            for (int i = 0; i < inputs; i++) {
                data.append(i == 0 ? "" : ",").append("{\"embedding\":[").append(i + 1).append(".5,0.25],\"index\":").append(i).append("}");
            }
            // No "usage", "model" or "object" fields at all, like the compatibility endpoint can do.
            byte[] out = (status == 200 ? "{\"data\":[" + data + "]}" : "{\"error\":\"nope sk-SECRET\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private HttpEmbeddingModel model() {
        return new HttpEmbeddingModel("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/", "test-key", "m-1");
    }

    @Test
    void readsVectorsWithoutAUsageBlockAndSendsModelAndKey() {
        EmbeddingResponse response = model().call(new EmbeddingRequest(List.of("alpha", "beta"), null));
        assertThat(response.getResults()).hasSize(2);
        assertThat(response.getResults().get(1).getOutput()).containsExactly(2.5f, 0.25f);
        assertThat(bodies.get(0)).contains("\"model\":\"m-1\"").contains("alpha");
        assertThat(auth.get(0)).isEqualTo("Bearer test-key");
    }

    @Test
    void splitsLargeInputsIntoBatchesOfAHundred() {
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < 230; i++) {
            texts.add("text" + i);
        }
        EmbeddingResponse response = model().call(new EmbeddingRequest(texts, null));
        assertThat(response.getResults()).hasSize(230);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void anErrorStatusFailsWithTheCodeAndNeverTheBody() {
        status = 429;
        assertThatThrownBy(() -> model().call(new EmbeddingRequest(List.of("x"), null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("429")
                .hasMessageNotContaining("SECRET");
    }
}
