package org.example.vision;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Интеграция Vision System с Stheno LLM
 * Отправляет информацию о скриншотах в основную LLM для комментариев
 */
public class SthenoChatInterface {
    private static final Logger LOG = Logger.getLogger(SthenoChatInterface.class.getName());

    private final String sthenoBaaseUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final int TIMEOUT = 30000;

    public SthenoChatInterface(String stheneBaseUrl) {
        this.sthenoBaaseUrl = stheneBaseUrl;
        LOG.log(Level.INFO, "🧠 Инициализирована интеграция с Stheno на " + stheneBaseUrl);
    }

    /**
     * Отправляет информацию о скриншоте в Stheno для комментария
     *
     * @param analysis Результат анализа скриншота от Moondream
     * @return Комментарий от Нины
     */
    public String getScreenCommentFromNina(ScreenAnalysis analysis) {
        try {
            if (!analysis.isShouldComment()) {
                LOG.log(Level.FINEST, "🤐 Фильтр решил - Нина не будет комментировать");
                return null;
            }

            LOG.log(Level.FINE, "💬 Отправляем анализ в Stheno для комментария");

            // Строим контекст для Нины
            String context = buildNinaContext(analysis);

            // Отправляем запрос
            String response = sendChatRequest(context);

            LOG.log(Level.FINE, "✅ Получен комментарий от Нины");
            return response;

        } catch (Exception e) {
            LOG.log(Level.WARNING, "❌ Ошибка при получении комментария: " + e.getMessage());
            return null;
        }
    }

    /**
     * Строит контекст для Нины на основе анализа скриншота
     */
    private String buildNinaContext(ScreenAnalysis analysis) {
        StringBuilder context = new StringBuilder();

        context.append("📸 Новый скриншот экрана:\n");
        context.append("─".repeat(50)).append("\n");
        context.append("Что видно: ").append(analysis.getDescription()).append("\n");
        context.append("Событие: ").append(analysis.getEventsSummary()).append("\n");

        // Добавляем подсказки для Нины в зависимости от типа события
        if (analysis.hasAnomalies()) {
            context.append("\n💡 Подсказка: На экране что-то идёт не так. Нина может помочь или посочувствовать.");
        }
        if (analysis.hasHumor()) {
            context.append("\n💡 Подсказка: Это смешно! Нина будет реагировать с юмором и сарказмом.");
        }
        if (analysis.hasUserAction()) {
            context.append("\n💡 Подсказка: Пользователь что-то делает. Нина может прокомментировать.");
        }

        context.append("\n").append("─".repeat(50)).append("\n");
        context.append("Дай естественный, краткий комментарий от Нины. Не более 1-2 предложений.\n");

        return context.toString();
    }

    /**
     * Отправляет запрос в чат Stheno
     */
    private String sendChatRequest(String message) throws Exception {
        URL url = new URL(sthenoBaaseUrl + "/api/chat");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(TIMEOUT);
        conn.setReadTimeout(TIMEOUT);
        conn.setDoOutput(true);

        // Подготавливаем JSON запрос
        Map<String, Object> request = new HashMap<>();
        request.put("message", message);
        request.put("model", "stheno");
        request.put("stream", false);

        String jsonRequest = objectMapper.writeValueAsString(request);

        // Отправляем
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonRequest.getBytes());
            os.flush();
        }

        // Читаем ответ
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            LOG.log(Level.WARNING, "⚠️ Stheno вернул код: " + responseCode);
            throw new IOException("Ошибка Stheno API: " + responseCode);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
        }

        // Парсим ответ
        Map<String, Object> responseMap = objectMapper.readValue(response.toString(), Map.class);
        return (String) responseMap.getOrDefault("response", "");
    }

    /**
     * Проверяет доступность Stheno
     */
    public boolean isStheneAvailable() {
        try {
            URL url = new URL(sthenoBaaseUrl + "/api/health");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            boolean available = code == 200;

            if (available) {
                LOG.log(Level.INFO, "✅ Stheno доступна");
            } else {
                LOG.log(Level.WARNING, "⚠️ Stheno недоступна (код " + code + ")");
            }

            return available;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "❌ Ошибка при проверке Stheno: " + e.getMessage());
            return false;
        }
    }
}

