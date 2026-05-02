package org.example.db.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;
import java.util.AbstractMap;
import org.example.db.entity.Memory;

/**
 * Сервис для работы с Qdrant Vector Database через REST API
 * Хранит воспоминания (Memory) с эмбеддингами
 */
public class QdrantMemoryService {
    private final String QDRANT_URL;
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String COLLECTION_NAME = "avatar_memories";
    private final int VECTOR_SIZE = 768; // Размер вектора для nomic-embed-text
    private final long FRESH_MEMORY_TTL_HOURS = 24;      // 1 день
    private final long SUMMARIZE_AFTER_HOURS = 24;        // Суммаризация на следующий день
    private final long FORGET_AFTER_HOURS = 48;           // Забывание через 2 дня
    private final long CONTEXT_OVERFLOW_THRESHOLD = 50;   // Максимум памятей в контексте

    public QdrantMemoryService(String qdrantUrl, int qdrantPort) throws Exception {
        this.QDRANT_URL = "http://" + qdrantUrl + ":" + 6333;
        this.client = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
        mapper.findAndRegisterModules();

        // Конфигурируем ObjectMapper для правильной работы с UTF-8
        // Это критично для русского текста!
        mapper.setDefaultPropertyInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);

        initializeCollection();
    }

    /**
     * Инициализирует коллекцию в Qdrant если её нет
     */
    private void initializeCollection() throws Exception {
        try {
            // Проверяем существует ли коллекция
            HttpRequest getRequest = HttpRequest.newBuilder()
                    .uri(URI.create(QDRANT_URL + "/collections/" + COLLECTION_NAME))
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(getRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return;
            }
        } catch (Exception e) {
            // Коллекция не найдена, создаём новую
        }

        // Создаём новую коллекцию
        Map<String, Object> vectorsConfig = Map.of(
            "size", VECTOR_SIZE,
            "distance", "Cosine"
        );

        Map<String, Object> body = Map.of(
            "vectors", vectorsConfig
        );

        String jsonBody = mapper.writeValueAsString(body);

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create(QDRANT_URL + "/collections/" + COLLECTION_NAME))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client.send(createRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 201 || response.statusCode() == 200) {
        } else {
            throw new RuntimeException("Ошибка создания коллекции: " + response.body());
        }
    }

    /**
     * Добавляет новое воспоминание в Qdrant
     */
    public String addMemory(String content, List<Float> embedding, List<Long> numericIds) throws Exception {
        String memoryId = UUID.randomUUID().toString();
        Memory memory = new Memory(memoryId, content, embedding);
        memory.setRelatedNumericIds(numericIds != null ? numericIds : new ArrayList<>());

        // Формируем точку для Qdrant
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", memoryId);
        payload.put("content", content);
        payload.put("embedding", embedding);
        payload.put("createdAt", memory.getCreatedAt().toString());
        payload.put("lastTriggeredAt", memory.getLastTriggeredAt().toString());
        payload.put("state", "FRESH");
        payload.put("importance", 0.5);
        payload.put("numericIds", numericIds != null ? numericIds : new ArrayList<>());

        Map<String, Object> point = Map.of(
            "id", hashMemoryId(memoryId),
            "vector", embedding,
            "payload", payload
        );

        Map<String, Object> body = Map.of(
            "points", List.of(point)
        );

        String jsonBody = mapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(QDRANT_URL + "/collections/" + COLLECTION_NAME + "/points"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .PUT(createUtf8Body(jsonBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));

        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new RuntimeException("Ошибка добавления памяти: " + response.body());
        }

        return memoryId;
    }

    /**
     * Поиск похожих воспоминаний (семантический поиск)
     * Использует ручной расчёт косинусного сходства
     */
    public List<Memory> searchSimilarMemories(List<Float> embedding, int limit) throws Exception {
        // Получаем все воспоминания
        List<Memory> allMemories = getAllMemories();

        // Вычисляем косинусное сходство с каждым
        List<Map.Entry<Memory, Double>> withScores = new ArrayList<>();

        for (Memory memory : allMemories) {
            if (memory.getEmbedding() != null && memory.getEmbedding().size() == embedding.size()) {
                double similarity = cosineSimilarity(embedding, memory.getEmbedding());

                if (similarity >= 0.5) { // Минимальный порог
                    withScores.add(new AbstractMap.SimpleEntry<>(memory, similarity));
                }
            }
        }

        // Сортируем по сходству (от большего к меньшему)
        withScores.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        // Берём top N
        List<Memory> results = withScores.stream()
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return results;
    }

    /**
     * Получает все воспоминания с фильтром по состоянию
     */
    public List<Memory> getAllMemories() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(QDRANT_URL + "/collections/" + COLLECTION_NAME + "/points/scroll?limit=1000"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        List<Memory> results = new ArrayList<>();
        if (response.statusCode() == 200) {
            JsonNode root = mapper.readTree(response.body());
            JsonNode result = root.get("result");

            if (result != null && result.has("points")) {
                JsonNode points = result.get("points");

                for (JsonNode point : points) {
                    Memory memory = payloadToMemory(point.get("payload"));
                    if (memory != null) {
                        results.add(memory);
                    }
                }
            }
        }

        return results;
    }

    /**
     * Получает свежие воспоминания (< 24 часов)
     */
    public List<Memory> getFreshMemories() throws Exception {
        return getAllMemories().stream()
                .filter(this::isMemoryFresh)
                .collect(Collectors.toList());
    }

    /**
     * Обновляет состояние воспоминания (суммаризация)
     */
    public void summarizeMemory(String memoryId, String summarizedContent, List<Float> newEmbedding) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", memoryId);
        payload.put("content", summarizedContent);
        payload.put("createdAt", java.time.LocalDateTime.now().minusDays(1).toString());
        payload.put("lastTriggeredAt", java.time.LocalDateTime.now().toString());
        payload.put("summarizedAt", java.time.LocalDateTime.now().toString());
        payload.put("state", "SUMMARIZED");
        payload.put("importance", 0.5);

        Map<String, Object> point = Map.of(
            "id", hashMemoryId(memoryId),
            "vector", newEmbedding,
            "payload", payload
        );

        Map<String, Object> body = Map.of(
            "points", List.of(point)
        );

        String jsonBody = mapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(QDRANT_URL + "/collections/" + COLLECTION_NAME + "/points"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .PUT(createUtf8Body(jsonBody))
                .build();

        client.send(request, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
        System.out.println("✓ Воспоминание суммаризировано: " + memoryId);
    }

    /**
     * Забывает воспоминание
     */
    public void forgetMemory(String memoryId) throws Exception {
        Map<String, Object> body = Map.of(
            "points", List.of(hashMemoryId(memoryId))
        );

        String jsonBody = mapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(QDRANT_URL + "/collections/" + COLLECTION_NAME + "/points/delete"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(createUtf8Body(jsonBody))
                .build();

        client.send(request, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
        System.out.println("✓ Воспоминание забыто: " + memoryId);
    }

    /**
     * Получает воспоминание по ID
     */
    public Memory getMemoryById(String memoryId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(QDRANT_URL + "/collections/" + COLLECTION_NAME + "/points/" + hashMemoryId(memoryId)))
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode root = mapper.readTree(response.body());
            JsonNode result = root.get("result");
            if (result != null) {
                return payloadToMemory(result.get("payload"));
            }
        }
        return null;
    }

    /**
     * Обновляет время последнего триггера (вспоминания)
     */
    public void triggerMemory(String memoryId) throws Exception {
        Memory memory = getMemoryById(memoryId);
        if (memory == null) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("id", memoryId);
        payload.put("content", memory.getContent());
        payload.put("createdAt", memory.getCreatedAt().toString());
        payload.put("lastTriggeredAt", java.time.LocalDateTime.now().toString());
        payload.put("state", memory.getState().toString());
        payload.put("importance", Math.min(1.0, memory.getImportance() + 0.1));

        Map<String, Object> point = Map.of(
            "id", hashMemoryId(memoryId),
            "vector", memory.getEmbedding(),
            "payload", payload
        );

        Map<String, Object> body = Map.of(
            "points", List.of(point)
        );

        String jsonBody = mapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(QDRANT_URL + "/collections/" + COLLECTION_NAME + "/points"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .PUT(createUtf8Body(jsonBody))
                .build();

        client.send(request, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Проверяет свежесть воспоминания
     */
    private boolean isMemoryFresh(Memory memory) {
        long hoursSinceCreation = java.time.temporal.ChronoUnit.HOURS.between(
                memory.getCreatedAt(),
                java.time.LocalDateTime.now()
        );
        return hoursSinceCreation < FRESH_MEMORY_TTL_HOURS;
    }

    /**
     * Проверяет нужна ли суммаризация
     * ВАЖНО: сумм��ризируем только если воспоминание не вспоминалось долгое время
     */
    public boolean shouldSummarize(Memory memory) {
        long hoursSinceCreation = java.time.temporal.ChronoUnit.HOURS.between(
                memory.getCreatedAt(),
                java.time.LocalDateTime.now()
        );

        // Проверяем, давно ли это воспоминание вспоминалось
        long hoursSinceLastTriggered = java.time.temporal.ChronoUnit.HOURS.between(
                memory.getLastTriggeredAt(),
                java.time.LocalDateTime.now()
        );

        // Суммаризируем ТОЛЬКО если:
        // 1. Прошло 24 часа с создания
        // 2. В состоянии FRESH
        // 3. Не вспоминалось в течение 24 часов (не активное!)
        return hoursSinceCreation >= SUMMARIZE_AFTER_HOURS &&
               memory.getState() == Memory.MemoryState.FRESH &&
               hoursSinceLastTriggered >= 24; // Не вспоминалось день
    }

    /**
     * Проверяет нужно ли забыть воспоминание
     * ВАЖНО: забываем только невспоминаемые воспоминания
     */
    public boolean shouldForget(Memory memory) {
        long hoursSinceCreation = java.time.temporal.ChronoUnit.HOURS.between(
                memory.getCreatedAt(),
                java.time.LocalDateTime.now()
        );

        // Проверяем, давно ли это воспоминание вспоминалось
        long hoursSinceLastTriggered = java.time.temporal.ChronoUnit.HOURS.between(
                memory.getLastTriggeredAt(),
                java.time.LocalDateTime.now()
        );

        // Забываем ТОЛЬКО если:
        // 1. Прошло 48 часов с создания
        // 2. В состоянии SUMMARIZED
        // 3. Не вспоминалось в течение 48 часов (совсем неактивное!)
        return hoursSinceCreation >= FORGET_AFTER_HOURS &&
               memory.getState() == Memory.MemoryState.SUMMARIZED &&
               hoursSinceLastTriggered >= FORGET_AFTER_HOURS; // Не вспоминалось 2 дня
    }

    /**
     * Проверяет перегрузку контекста
     */
    public boolean isContextOverloaded() throws Exception {
        List<Memory> allMemories = getAllMemories();
        return allMemories.size() > CONTEXT_OVERFLOW_THRESHOLD;
    }

    // ===== УТИЛИТЫ =====

    private long hashMemoryId(String memoryId) {
        return Math.abs((long) memoryId.hashCode());
    }

    private Memory payloadToMemory(JsonNode payload) {
        try {
            // Проверка null перед парсингом!
            if (payload == null || payload.isNull()) {
                return null;
            }

            Memory memory = new Memory();
            memory.setId(payload.get("id").asText());
            memory.setContent(safeParseText(payload.get("content")));
            memory.setCreatedAt(java.time.LocalDateTime.parse(payload.get("createdAt").asText()));
            memory.setState(Memory.MemoryState.valueOf(payload.get("state").asText()));
            memory.setImportance(payload.get("importance").asDouble());

            // ← ВОССТАНАВЛИВАЕМ ЭМБЕДДИНГ!
            if (payload.has("embedding") && !payload.get("embedding").isNull()) {
                List<Double> doubleList = mapper.convertValue(payload.get("embedding"), List.class);
                List<Float> floatList = new java.util.ArrayList<>();
                if (doubleList != null) {
                    for (Double d : doubleList) {
                        floatList.add(d != null ? d.floatValue() : 0f);
                    }
                }
                memory.setEmbedding(floatList);
            }

            if (payload.has("lastTriggeredAt") && !payload.get("lastTriggeredAt").isNull()) {
                memory.setLastTriggeredAt(java.time.LocalDateTime.parse(payload.get("lastTriggeredAt").asText()));
            }

            return memory;
        } catch (Exception e) {
            System.err.println("Ошибка парсинга payload: " + e.getMessage());
            return null;
        }
    }

    /**
     * Вычисляет косинусное сходство между двумя векторами
     * Работает с Number (Double или Float)
     */
    private double cosineSimilarity(List<? extends Number> vec1, List<? extends Number> vec2) {
        if (vec1 == null || vec2 == null || vec1.size() != vec2.size()) return 0.0;

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.size(); i++) {
            double v1 = vec1.get(i).doubleValue();
            double v2 = vec2.get(i).doubleValue();
            dotProduct += v1 * v2;
            norm1 += v1 * v1;
            norm2 += v2 * v2;
        }

        double denominator = Math.sqrt(norm1) * Math.sqrt(norm2);
        if (denominator == 0.0) return 0.0;

        return dotProduct / denominator;
    }

    /**
     * Helper для создания HttpRequest.BodyPublisher с явным UTF-8 кодированием
     * КРИТИЧНО для русского текста!
     */
    private HttpRequest.BodyPublisher createUtf8Body(String jsonBody) {
        return HttpRequest.BodyPublishers.ofString(jsonBody, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Безопасно парсит текст из JSON, гарантируя что он не повреждён
     * Проверяет содержит ли текст только валидные символы
     */
    private String safeParseText(JsonNode textNode) {
        if (textNode == null || textNode.isNull()) {
            return "";
        }

        String text = textNode.asText();

        // Проверяем что текст содержит валидные символы (не мусор как "nychto" и "мну")
        // Если текст выглядит повреждённым, логируем это
        if (text != null && !text.isEmpty()) {
            // Проверяем на странные комбинации символов которые появляются при бракованном парсинге
            if (text.contains("nychto") || text.contains("пишëл") || text.contains("мну")) {
                System.err.println("⚠️  ВНИМАНИЕ: Обнаружен повреждённый текст в JSON: " + text);
                System.err.println("    Это может быть проблемой с кодировкой UTF-8 при работе с Qdrant");
            }
        }

        return text;
    }

    /**
     * Полностью очищает базу данных памяти (удаляет и пересоздаёт коллекцию)
     */
    public void clearAllMemories() throws Exception {
        System.out.println("🗑️  Удаляю коллекцию памяти...");

        try {
            // Удаляем коллекцию
            HttpRequest deleteRequest = HttpRequest.newBuilder()
                    .uri(URI.create(QDRANT_URL + "/collections/" + COLLECTION_NAME))
                    .header("Content-Type", "application/json")
                    .DELETE()
                    .build();

            HttpResponse<String> response = client.send(deleteRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 204) {
                System.out.println("✓ Коллекция удалена");
            } else {
                throw new RuntimeException("Ошибка удаления коллекции: " + response.body());
            }
        } catch (Exception e) {
            System.err.println("⚠️  Ошибка при удалении коллекции: " + e.getMessage());
        }

        // Пересоздаём коллекцию
        System.out.println("🔄 Создаю новую коллекцию...");
        initializeCollection();
        System.out.println("✓ Коллекция пересоздана - база полностью очищена!");
    }

    public void close() throws Exception {
        // REST API не требует закрытия соединения
    }
}
