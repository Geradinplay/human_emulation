package org.example.vision;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Клиент для работы с Ollama Vision API (Moondream)
 */
public class OllamaVisionClient {
    private static final Logger LOG = Logger.getLogger(OllamaVisionClient.class.getName());

    private final String baseUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String MODEL = "moondream";
    private static final int TIMEOUT = 30000; // 30 секунд

    public OllamaVisionClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Анализирует изображение через Moondream
     *
     * @param imagePath Путь к изображению
     * @return Текстовое описание изображения
     */
    public String analyzeImage(String imagePath) throws Exception {
        LOG.log(Level.FINE, "🔍 Отправляем изображение на анализ в Moondream");

        // Читаем изображение и кодируем в base64
        byte[] imageBytes = Files.readAllBytes(Paths.get(imagePath));
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        // Создаём запрос
        Map<String, Object> request = new HashMap<>();
        request.put("model", MODEL);
        request.put("prompt", "Опиши подробно что ты видишь на этом скриншоте. Что делает пользователь? Есть ли ошибки, необычные элементы или интересные моменты?");
        request.put("images", Collections.singletonList(base64Image));
        request.put("stream", false);

        // Отправляем запрос
        String response = sendRequest("/api/generate", request);

        // Парсим ответ
        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

        if (responseMap.containsKey("response")) {
            String description = (String) responseMap.get("response");
            LOG.log(Level.FINE, "✅ Получено описание: " + description.substring(0, Math.min(100, description.length())) + "...");
            return description.trim();
        }

        LOG.log(Level.WARNING, "⚠️ Пустой ответ от Moondream");
        return "";
    }

    /**
     * Отправляет запрос к Ollama API
     */
    private String sendRequest(String endpoint, Map<String, Object> payload) throws Exception {
        URL url = new URL(baseUrl + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(TIMEOUT);
        conn.setReadTimeout(TIMEOUT);
        conn.setDoOutput(true);

        // Отправляем JSON
        String jsonPayload = objectMapper.writeValueAsString(payload);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonPayload.getBytes());
            os.flush();
        }

        // Читаем ответ
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("Ошибка Ollama API: " + responseCode);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
        }

        return response.toString();
    }

    /**
     * Проверяет доступность Ollama сервера
     */
    public boolean isAvailable() {
        try {
            URL url = new URL(baseUrl + "/api/tags");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            return code == 200;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "❌ Ollama недоступна: " + e.getMessage());
            return false;
        }
    }

    /**
     * Проверяет установлена ли модель Moondream
     */
    public boolean hasMoondream() throws Exception {
        URL url = new URL(baseUrl + "/api/tags");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
        }

        Map<String, Object> tagsResponse = objectMapper.readValue(response.toString(), Map.class);
        List<Map<String, Object>> models = (List<Map<String, Object>>) tagsResponse.get("models");

        return models != null && models.stream()
            .anyMatch(m -> MODEL.equals(m.get("name")));
    }
}

