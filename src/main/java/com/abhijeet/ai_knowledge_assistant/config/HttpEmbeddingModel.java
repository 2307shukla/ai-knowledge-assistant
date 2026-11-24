package com.abhijeet.ai_knowledge_assistant.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Embeddings over plain HTTP against an OpenAI-compatible {@code /embeddings} endpoint (Google Gemini
 * by default). The OpenAI SDK behind Spring AI 2 insists on response fields such as {@code usage} that
 * Gemini's compatibility endpoint does not reliably send, which made every embedding call fail. This
 * client reads only {@code data[].embedding}, so a missing optional field cannot break indexing.
 */
public class HttpEmbeddingModel implements EmbeddingModel {

    /** Gemini accepts up to 100 inputs per request. */
    static final int BATCH_SIZE = 100;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final String endpoint;
    private final String apiKey;
    private final String model;

    public HttpEmbeddingModel(String baseUrl, String apiKey, String model) {
        this.endpoint = baseUrl.replaceAll("/+$", "") + "/embeddings";
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request.getInstructions();
        List<Embedding> out = new ArrayList<>();
        for (int from = 0; from < texts.size(); from += BATCH_SIZE) {
            List<String> batch = texts.subList(from, Math.min(texts.size(), from + BATCH_SIZE));
            for (float[] vector : embedBatch(batch)) {
                out.add(new Embedding(vector, out.size()));
            }
        }
        return new EmbeddingResponse(out);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    private List<float[]> embedBatch(List<String> batch) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            mapper.writeValueAsString(Map.of("model", model, "input", batch))))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                // The body is deliberately left out: it can echo request details.
                throw new IllegalStateException("Embedding request failed with HTTP " + response.statusCode());
            }
            JsonNode data = mapper.readTree(response.body()).path("data");
            if (!data.isArray() || data.size() != batch.size()) {
                throw new IllegalStateException("Embedding response did not contain one vector per input");
            }
            float[][] ordered = new float[batch.size()][];
            for (int i = 0; i < data.size(); i++) {
                JsonNode item = data.get(i);
                int index = item.has("index") ? item.get("index").asInt(i) : i;
                JsonNode values = item.path("embedding");
                if (index < 0 || index >= ordered.length || !values.isArray() || values.isEmpty()) {
                    throw new IllegalStateException("Embedding response was malformed");
                }
                float[] vector = new float[values.size()];
                for (int j = 0; j < vector.length; j++) {
                    vector[j] = (float) values.get(j).asDouble();
                }
                ordered[index] = vector;
            }
            List<float[]> result = new ArrayList<>(ordered.length);
            for (float[] vector : ordered) {
                if (vector == null) {
                    throw new IllegalStateException("Embedding response was malformed");
                }
                result.add(vector);
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Embedding request could not be completed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Embedding request was interrupted", e);
        }
    }
}
