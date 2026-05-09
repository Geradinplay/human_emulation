package org.example;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.db.*;
import org.example.db.entity.Memory;
import org.example.db.entity.NumericParameter;
import org.example.db.service.EmbeddingService;
import org.example.db.service.QdrantMemoryService;
import org.example.vts.VTSLauncher;
import org.example.vts.client.ExpressionHandlerStub;
import org.example.vts.client.VTubeStudioClient;


public class AvatarBrain {

    private final String OLLAMA_URL;
    private final String OLLAMA_MODEL;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConfigLoader config = ConfigLoader.getInstance();

    // Компоненты памяти
    private MemoryManager memoryManager;
    private MemoryDaemon memoryDaemon;

    // VTube Studio интеграция
    private VTSLauncher vtsLauncher;
    private boolean vtubeEnabled = false;


    public AvatarBrain() {
        this.OLLAMA_URL = config.getOllamaUrl();
        this.OLLAMA_MODEL = config.getOllamaModel();
    }

    /**
     * Инициализирует систему памяти
     */
    public void initializeMemory(){
        try {
            initializeMemory(config.getQdrantHost(), config.getQdrantPort());
        } catch (Exception e) {
            throw new RuntimeException("Ошибка инициализации памяти: " + e.getMessage(), e);
        }
    }
    public VTubeStudioClient getClient(){
        return vtsLauncher.getClient();
    }

    public void initializeMemory(String qdrantUrl, int qdrantPort) throws Exception {
        QdrantMemoryService qdrantService = new QdrantMemoryService(qdrantUrl, qdrantPort);
        EmbeddingService embeddingService = new EmbeddingService();
        memoryManager = new MemoryManager(qdrantService, embeddingService, this);
        memoryDaemon = new MemoryDaemon(memoryManager);
        memoryDaemon.start();
    }

    public boolean initializeVTubeStudio() {
        return initializeVTubeStudio("ws://localhost:8001");
    }

    public boolean initializeVTubeStudio(String pluginName, String pluginDeveloper, String wsUrl) {
        return initializeVTubeStudio(wsUrl);
    }

    private boolean initializeVTubeStudio(String wsUrl) {
        try {
            vtsLauncher = new VTSLauncher(new URI(wsUrl));
            vtsLauncher.start();
            Thread.sleep(3000);
            if (vtsLauncher.getClient().isOpen() && vtsLauncher.getClient().isAuthenticated()) {
                vtubeEnabled = true;
                System.out.println("✅ VTube Studio интеграция инициализирована: " + wsUrl);
                return true;
            } else {
                vtubeEnabled = false;
                System.err.println("❌ Не удалось подключиться или авторизоваться в VTube Studio");
                return false;
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка инициализации VTube Studio: " + e.getMessage());
            vtubeEnabled = false;
            return false;
        }
    }


    public VTSLauncher getLauncher() {
        return this.vtsLauncher;
    }
    public boolean isVTubeStudioEnabled() {
        return vtubeEnabled && vtsLauncher != null;
    }
    /**
     * Проброс для ручного управления эмоциями из GUI.
     */
    public void triggerManualExpression(String expName) {
        if (vtsLauncher != null && vtsLauncher.getMovementManager() != null) {
            vtsLauncher.triggerExpression(expName);
        }
    }

    public void triggerAnimatedExpression(String expName, String animName) {
        if (vtsLauncher != null && vtsLauncher.getMovementManager() != null) {
            vtsLauncher.triggerAnimatedExpression(expName, animName);
        }
    }

    /**
     * Извлекает эмоции и веса из ответа и отправляет их в VTube Studio
     */


    public void disconnectVTubeStudio() {
        if (vtsLauncher != null) {
            vtsLauncher.resetAllEmotions();
            vtsLauncher.stopAll();
            vtubeEnabled = false;
            System.out.println("✅ VTube Studio отключена");
        }
    }


    public void triggerManualAnimation(String animationName) {
        vtsLauncher.triggerAnimation(animationName);
    }

    /** Извлекает эмоции и анимации и отправляет их в VTube Studio */
    public void sendEmotionalResponse(String response) {
        if (!isVTubeStudioEnabled()) return;

        try {

            ExpressionHandlerStub handler = getLauncher().getExpressionHandler();

            String expression = handler.extractExpression(response);
            String animation = handler.extractAnimation(response);

            if (expression == null || animation == null) {
                triggerAnimatedExpression(expression, animation);
            }

            if (animation != null) {
                triggerManualAnimation(animation);
            }

            if (expression != null) {
                triggerManualExpression(expression);
            }

        } catch (Exception e) {
            System.err.println("⚠️ Ошибка VTS: " + e.getMessage());
        }
    }

    public String think(String userMessage) throws Exception {
        String response = generateResponse(userMessage);

        // Отправляем команды в VTube Studio
        sendEmotionalResponse(response);

        // Сохраняем в память
        if (memoryManager != null) {
            try {
                memoryManager.createMemory("Пользователь: " + userMessage, null);
                memoryManager.createMemory("Нина: " + response, null);
            } catch (Exception e) {
                System.err.println("⚠️ Ошибка сохранения в память: " + e.getMessage());
            }
        }

        // Очищаем текст от технического блока [animation: ..., expression: ...]
        // Регулярка удаляет всё в первых квадратных скобках
        return response.replaceAll("^\\[.*?\\]", "").trim();
    }




    private String generateResponse(String userMessage) throws Exception {
        String systemInstruction = config.getSystemInstruction();
        String userPrompt = userMessage;

        if (memoryManager != null) {
            try {
                List<Memory> relevantMemories = memoryManager.searchMemories(userMessage);
                if (!relevantMemories.isEmpty()) {
                    StringBuilder context = new StringBuilder("\n" + config.getMemoryContextHeader() + "\n");
                    int maxResults = config.getMaxSearchResults();
                    for (Memory memory : relevantMemories.stream().limit(maxResults).collect(Collectors.toList())) {
                        context.append("• ").append(memory.getContent()).append("\n");
                    }
                    context.append(config.getMemoryContextFooter() + "\n");
                    userPrompt = context + "\n" + userMessage;
                }
            } catch (Exception e) {
                System.err.println("⚠️ Ошибка получения контекста: " + e.getMessage());
            }
        }

        String contextPrompt = String.format(
                "<|start_header_id|>system<|end_header_id|>\n\n%s<|eot_id|>" +
                        "<|start_header_id|>user<|end_header_id|>\n\n%s<|eot_id|>" +
                        "<|start_header_id|>assistant<|end_header_id|>\n\n",
                systemInstruction.trim(), userPrompt.trim()
        );

        Map<String, Object> body = new HashMap<>();
        body.put("model", OLLAMA_MODEL);
        body.put("prompt", contextPrompt);
        body.put("stream", false);
        body.put("options", Map.of(
                "num_predict", config.getNumPredict(),
                "temperature", config.getTemperature(),
                "top_p", config.getTopP()
        ));

        String jsonBody = mapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();
        if (responseBody == null || responseBody.isEmpty()) return "Ошибка Ollama.";

        com.fasterxml.jackson.databind.JsonNode responseNode = mapper.readTree(responseBody);
        String result = responseNode.get("response").asText();

        return cleanResponseFromContext(result);
    }

    private String cleanResponseFromContext(String result) {
        if (result == null || result.isEmpty()) return result;
        // Очистка от мусора Саиги, если она повторяет промпт
        if (result.contains("<|start_header_id|>")) {
            int lastHeader = result.lastIndexOf("<|start_header_id|>assistant<|end_header_id|>");
            if (lastHeader != -1) result = result.substring(lastHeader + 45);
        }
        return result.trim();
    }

    // --- ОСТАЛЬНЫЕ МЕТОДЫ ---
    public String rememberThis(String content, List<String> numericParams) throws Exception {
        if (memoryManager == null) throw new RuntimeException("Память не инициализирована.");
        return memoryManager.createMemory(content, numericParams);
    }

    public List<Memory> searchMemory(String query) throws Exception {
        return (memoryManager == null) ? Collections.emptyList() : memoryManager.searchMemories(query);
    }

    public void clearAllMemories() throws Exception {
        if (memoryManager != null) memoryManager.clearAllMemories();
    }

    public Map<Long, NumericParameter> getNumericParameters() {
        return (memoryManager == null) ? Collections.emptyMap() : memoryManager.getAllNumericParameters();
    }

    public void shutdown() {
        if (memoryDaemon != null) memoryDaemon.stop();
        disconnectVTubeStudio();
    }

    public boolean isMemoryRunning() {
        return memoryDaemon != null && memoryDaemon.isRunning();
    }

    private boolean isValidEmotionTag(String tag) {
        return List.of("angry", "blush", "smile", "suspicious", "happy").contains(tag.toLowerCase());
    }

    public static void main(String[] args) {

    }
}