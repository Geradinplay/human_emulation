package org.example.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Утилита для экспорта и импорта базы данных Qdrant
 */
public class QdrantBackupManager {
    private final String qdrantUrl;
    private final ObjectMapper objectMapper;
    private static final String BACKUP_DIR = "backups";

    public QdrantBackupManager(String qdrantUrl) {
        this.qdrantUrl = qdrantUrl.endsWith("/") ? qdrantUrl.substring(0, qdrantUrl.length() - 1) : qdrantUrl;
        this.objectMapper = new ObjectMapper();

        // Создаём директорию для бэкапов если её нет
        try {
            Files.createDirectories(Paths.get(BACKUP_DIR));
        } catch (IOException e) {
            System.err.println("Ошибка создания директории бэкапов: " + e.getMessage());
        }
    }

    /**
     * Экспорт всех коллекций в JSON файл
     */
    public String exportDatabase(String collectionName) throws Exception {
        // Проверяем, существует ли коллекция
        if (!collectionExists(collectionName)) {
            throw new RuntimeException("Коллекция '" + collectionName + "' не найдена в Qdrant. Проверьте что Qdrant запущена и содержит данные.");
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String filename = String.format("%s/%s_backup_%s.json", BACKUP_DIR, collectionName, timestamp);

        // Получаем все точки из коллекции
        Map<String, Object> allPoints = new LinkedHashMap<>();
        allPoints.put("collection", collectionName);
        allPoints.put("exported_at", timestamp);
        allPoints.put("points", getAllPoints(collectionName));

        // Сохраняем в файл
        String jsonContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(allPoints);
        Files.write(Paths.get(filename), jsonContent.getBytes());

        return filename;
    }

    /**
     * Проверить существование коллекции
     */
    private boolean collectionExists(String collectionName) throws Exception {
        try {
            String url = String.format("%s/api/v1/collections/%s", qdrantUrl, collectionName);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Импорт базы из JSON файла
     */
    public void importDatabase(String filePath, String collectionName) throws Exception {
        String jsonContent = new String(Files.readAllBytes(Paths.get(filePath)));
        Map<String, Object> data = objectMapper.readValue(jsonContent, Map.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = (List<Map<String, Object>>) data.get("points");

        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("Нет точек для импорта");
        }

        // Убедимся, что коллекция существует
        ensureCollectionExists(collectionName, 384); // 384-мерные эмбеддинги

        // Импортируем точки батчами
        int batchSize = 100;
        for (int i = 0; i < points.size(); i += batchSize) {
            int end = Math.min(i + batchSize, points.size());
            List<Map<String, Object>> batch = points.subList(i, end);
            addPointsBatch(collectionName, batch);
        }
    }

    /**
     * Получить все точки из коллекции
     */
    private List<Map<String, Object>> getAllPoints(String collectionName) throws Exception {
        List<Map<String, Object>> allPoints = new ArrayList<>();
        int offset = 0;
        int limit = 100;
        boolean hasMore = true;

        while (hasMore) {
            String url = String.format("%s/api/v1/collections/%s/points?limit=%d&offset=%d",
                qdrantUrl, collectionName, limit, offset);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                throw new RuntimeException(
                    "Коллекция '" + collectionName + "' не найдена (404).\n" +
                    "Проверьте что Qdrant запущена и содержит коллекцию 'memories'.\n" +
                    "Адрес: " + qdrantUrl
                );
            } else if (response.statusCode() != 200) {
                throw new RuntimeException("Ошибка получения точек: HTTP " + response.statusCode() +
                    "\nОтвет: " + response.body());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> responseData = objectMapper.readValue(response.body(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) responseData.get("result");

            if (result == null) {
                throw new RuntimeException("Неожиданный формат ответа от Qdrant: отсутствует 'result'");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> points = (List<Map<String, Object>>) result.get("points");

            if (points == null) {
                points = new ArrayList<>();
            }

            if (points.isEmpty()) {
                hasMore = false;
            } else {
                allPoints.addAll(points);
                offset += limit;
            }
        }

        return allPoints;
    }

    /**
     * Добавить батч точек в коллекцию
     */
    private void addPointsBatch(String collectionName, List<Map<String, Object>> points) throws Exception {
        String url = String.format("%s/api/v1/collections/%s/points?wait=true", qdrantUrl, collectionName);

        Map<String, Object> payload = new HashMap<>();
        payload.put("points", points);

        String jsonPayload = objectMapper.writeValueAsString(payload);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .timeout(java.time.Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new RuntimeException("Ошибка добавления точек: " + response.statusCode());
        }
    }

    /**
     * Убедиться, что коллекция существует
     */
    private void ensureCollectionExists(String collectionName, int vectorSize) throws Exception {
        String url = String.format("%s/api/v1/collections/%s", qdrantUrl, collectionName);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .timeout(java.time.Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            // Коллекция не существует, создаём её
            createCollection(collectionName, vectorSize);
        } else if (response.statusCode() != 200) {
            throw new RuntimeException("Ошибка проверки коллекции: " + response.statusCode());
        }
    }

    /**
     * Создать новую коллекцию
     */
    private void createCollection(String collectionName, int vectorSize) throws Exception {
        String url = String.format("%s/api/v1/collections", qdrantUrl);

        Map<String, Object> payload = new HashMap<>();
        payload.put("name", collectionName);

        Map<String, Object> vectorsConfig = new HashMap<>();
        vectorsConfig.put("size", vectorSize);
        vectorsConfig.put("distance", "Cosine");
        payload.put("vectors", vectorsConfig);

        String jsonPayload = objectMapper.writeValueAsString(payload);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .timeout(java.time.Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new RuntimeException("Ошибка создания коллекции: " + response.statusCode());
        }
    }

    /**
     * Получить список всех доступных бэкапов
     */
    public List<String> listBackups() {
        List<String> backups = new ArrayList<>();
        try {
            Files.list(Paths.get(BACKUP_DIR))
                    .filter(path -> path.toString().endsWith(".json"))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .sorted()
                    .forEach(backups::add);
        } catch (IOException e) {
            System.err.println("Ошибка списания бэкапов: " + e.getMessage());
        }
        return backups;
    }

    /**
     * Удалить коллекцию
     */
    public void deleteCollection(String collectionName) throws Exception {
        String url = String.format("%s/api/v1/collections/%s", qdrantUrl, collectionName);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .timeout(java.time.Duration.ofSeconds(5))
                .DELETE()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200 && response.statusCode() != 204) {
            throw new RuntimeException("Ошибка удаления коллекции: " + response.statusCode());
        }
    }

    /**
     * Получить размер коллекции
     */
    public long getCollectionSize(String collectionName) throws Exception {
        List<Map<String, Object>> points = getAllPoints(collectionName);
        return points.size();
    }
}

