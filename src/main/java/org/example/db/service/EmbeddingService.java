package org.example.db.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Сервис для генерации эмбеддингов через Ollama API
 */
public class EmbeddingService {
    private final String OLLAMA_URL = "http://localhost:11434/api/embed";
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String EMBEDDING_MODEL = "nomic-embed-text"; // Модель для эмбеддингов

    /**
     * Генерирует эмбеддинг для текста через Ollama
     * @param text Текст для эмбеддинга
     * @return Список float значений (вектор)
     */
    public List<Float> generateEmbedding(String text) throws Exception {
        Map<String, Object> body = Map.of(
                "model", EMBEDDING_MODEL,
                "input", text
        );
        String jsonBody = mapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_URL))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, java.nio.charset.StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new RuntimeException("Embedding service error: " + response.body());
        }

        // Парсим ответ и достаём embeddings с защитой от ошибок типов
        try {
            JsonNode jsonNode = mapper.readTree(response.body());
            JsonNode embeddingsNode = jsonNode.get("embeddings");

            if (embeddingsNode == null || embeddingsNode.isNull()) {
                throw new RuntimeException("embeddings field not found in response");
            }

            // Получаем первый embedding
            JsonNode embeddingNode = null;
            if (embeddingsNode.isArray() && embeddingsNode.size() > 0) {
                embeddingNode = embeddingsNode.get(0);
            } else {
                throw new RuntimeException("embeddings is not an array or is empty");
            }

            if (embeddingNode == null || !embeddingNode.isArray()) {
                throw new RuntimeException("First embedding is not an array");
            }

            // Конвертируем в List<Float> правильно - через Double сначала
            List<Double> doubleList = mapper.convertValue(embeddingNode, List.class);
            List<Float> floatList = new java.util.ArrayList<>();
            if (doubleList != null) {
                for (Double d : doubleList) {
                    floatList.add(d != null ? d.floatValue() : 0f);
                }
            }
            return floatList;
        } catch (Exception e) {
            System.err.println("❌ Ошибка парсинга эмбеддинга: " + e.getMessage());
            System.err.println("    Ответ от Ollama: " + response.body());
            throw e;
        }
    }

    /**
     * Генерирует эмбеддинги для нескольких текстов
     * @param texts Список текстов
     * @return Список списков float значений
     */
    public List<List<Float>> generateEmbeddings(List<String> texts) throws Exception {
        Map<String, Object> body = Map.of(
                "model", EMBEDDING_MODEL,
                "input", texts
        );
        String jsonBody = mapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_URL))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, java.nio.charset.StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new RuntimeException("Embedding service error: " + response.body());
        }

        try {
            JsonNode jsonNode = mapper.readTree(response.body());
            JsonNode embeddingsArray = jsonNode.get("embeddings");

            if (embeddingsArray == null || !embeddingsArray.isArray()) {
                throw new RuntimeException("embeddings field is not an array");
            }

            // Конвертируем каждый embedding правильно из Double в Float
            List<List<Float>> result = new java.util.ArrayList<>();
            for (JsonNode embedding : embeddingsArray) {
                if (embedding != null && embedding.isArray()) {
                    List<Double> doubleList = mapper.convertValue(embedding, List.class);
                    List<Float> floatList = new java.util.ArrayList<>();
                    if (doubleList != null) {
                        for (Double d : doubleList) {
                            floatList.add(d != null ? d.floatValue() : 0f);
                        }
                    }
                    result.add(floatList);
                }
            }
            return result;
        } catch (Exception e) {
            System.err.println("❌ Ошибка парсинга эмбеддингов: " + e.getMessage());
            System.err.println("    Ответ от Ollama: " + response.body());
            throw e;
        }
    }
}

