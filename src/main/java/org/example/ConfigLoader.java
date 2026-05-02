package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Загрузчик конфигурации из JSON файла
 */
public class ConfigLoader {
    private static ConfigLoader instance;
    private JsonNode config;
    private final ObjectMapper mapper = new ObjectMapper();

    private ConfigLoader() {
        loadConfig();
    }

    public static ConfigLoader getInstance() {
        if (instance == null) {
            instance = new ConfigLoader();
        }
        return instance;
    }

    private void loadConfig() {
        try (InputStream is = getClass().getResourceAsStream("/config.json")) {
            if (is == null) {
                throw new RuntimeException("config.json не найден в resources");
            }
            config = mapper.readTree(is);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка загрузки конфига: " + e.getMessage(), e);
        }
    }

    // Ollama
    public String getOllamaUrl() {
        return config.get("ollama").get("url").asText();
    }

    public String getOllamaModel() {
        return config.get("ollama").get("model").asText();
    }

    public int getNumPredict() {
        return config.get("ollama").get("num_predict").asInt();
    }

    public int getTopK() {
        return config.get("ollama").get("top_k").asInt();
    }

    public double getTopP() {
        return config.get("ollama").get("top_p").asDouble();
    }

    public double getTemperature() {
        return config.get("ollama").get("temperature").asDouble();
    }

    public int getNumThread() {
        return config.get("ollama").get("num_thread").asInt();
    }

    // Qdrant
    public String getQdrantHost() {
        return config.get("qdrant").get("host").asText();
    }

    public int getQdrantPort() {
        return config.get("qdrant").get("port").asInt();
    }

    public String getQdrantCollection() {
        return config.get("qdrant").get("collection").asText();
    }

    // Prompts
    public String getSystemInstruction() {
        try {
            String path = config.get("prompts").get("system_instruction_path").asText();
            // Пытаемся загрузить из ресурсов
            InputStream is = getClass().getResourceAsStream("/" + path);
            if (is != null) {
                return new String(is.readAllBytes());
            }
            // Если не найдено в ресурсах, пытаемся загрузить из файловой системы
            String filePath = "src/main/resources/" + path;
            if (Files.exists(Paths.get(filePath))) {
                return new String(Files.readAllBytes(Paths.get(filePath)));
            }
            throw new RuntimeException("Файл системной инструкции не найден: " + path);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка загрузки системной инструкции: " + e.getMessage(), e);
        }
    }

    public String getMemoryContextHeader() {
        return config.get("prompts").get("memory_context_header").asText();
    }

    public String getMemoryContextFooter() {
        return config.get("prompts").get("memory_context_footer").asText();
    }

    // Memory settings
    public int getMaxContextMemories() {
        return config.get("memory").get("max_context_memories").asInt();
    }

    public int getMaxSearchResults() {
        return config.get("memory").get("max_search_results").asInt();
    }

    public boolean isDaemonEnabled() {
        return config.get("memory").get("daemon_enabled").asBoolean();
    }

    // UI messages
    public String getStartupMessage() {
        return config.get("ui").get("startup_message").asText();
    }

    public String getPromptPrefix() {
        return config.get("ui").get("prompt_prefix").asText();
    }

    public String getAiPrefix() {
        return config.get("ui").get("ai_prefix").asText();
    }

    public String getLoadingIndicator() {
        return config.get("ui").get("loading_indicator").asText();
    }

    // System
    public String getSystemName() {
        return config.get("system").get("name").asText();
    }

    public String getSystemVersion() {
        return config.get("system").get("version").asText();
    }

    public String getLanguage() {
        return config.get("system").get("language").asText();
    }
}

