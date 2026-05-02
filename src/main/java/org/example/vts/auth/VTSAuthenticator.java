package org.example.vts.auth;

import com.google.gson.JsonObject;
import org.example.vts.log.VTSLogger;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class VTSAuthenticator {
    private static final String TOKEN_FILE = "vts_token.txt";
    private final String pluginName = "AI_Controller";
    private final String developer = "Geradine";
    private String cachedToken = null;

    public VTSAuthenticator() {
        loadToken();
    }

    // Запрос на получение НОВОГО токена (показывает окно в VTS)
    public String buildTokenRequestJson() {
        JsonObject request = new JsonObject();
        request.addProperty("apiName", "VTubeStudioPublicAPI");
        request.addProperty("apiVersion", "1.0");
        request.addProperty("requestID", "TokenRequest");
        request.addProperty("messageType", "AuthenticationTokenRequest");

        JsonObject data = new JsonObject();
        data.addProperty("pluginName", pluginName);
        data.addProperty("pluginDeveloper", developer);

        request.add("data", data);
        return request.toString();
    }

    // Запрос на авторизацию с имеющимся токеном
    public String buildAuthRequestJson() {
        if (cachedToken == null) return null;

        JsonObject request = new JsonObject();
        request.addProperty("apiName", "VTubeStudioPublicAPI");
        request.addProperty("apiVersion", "1.0");
        request.addProperty("requestID", "AuthRequest");
        request.addProperty("messageType", "AuthenticationRequest");

        JsonObject data = new JsonObject();
        data.addProperty("pluginName", pluginName);
        data.addProperty("pluginDeveloper", developer);
        data.addProperty("authenticationToken", cachedToken);

        request.add("data", data);
        return request.toString();
    }

    public void saveToken(String token) {
        this.cachedToken = token;
        try {
            Files.writeString(Paths.get(TOKEN_FILE), token);
            VTSLogger.info("Токен сохранен в файл: " + TOKEN_FILE);
        } catch (IOException e) {
            VTSLogger.error("Не удалось сохранить токен", e);
        }
    }

    private void loadToken() {
        try {
            if (Files.exists(Paths.get(TOKEN_FILE))) {
                this.cachedToken = Files.readString(Paths.get(TOKEN_FILE)).trim();
                VTSLogger.info("Токен загружен из файла.");
            }
        } catch (IOException e) {
            VTSLogger.info("Токен не найден, потребуется новая авторизация.");
        }
    }

    public boolean hasToken() {
        return cachedToken != null;
    }
}