package org.example.vts.client;



import org.example.vts.auth.VTSAuthListener;
import org.example.vts.auth.VTSAuthenticator;
import org.example.vts.log.VTSLogger;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class VTubeStudioClient extends WebSocketClient {
    private final Map<String, Double> targetPositions = new ConcurrentHashMap<>();

    // То, где голова находится сейчас (плавно ползет к цели)
    private final Map<String, Double> currentBasePositions = new ConcurrentHashMap<>();

    // Коэффициент возврата (чем меньше, тем медленнее и мягче возврат)
    private static final double RETURN_SMOOTHNESS = 0.05;
    // Хранилище для интерполяции (чтобы знать текущее положение параметров)
    private final Map<String, Double> lastSentValues = new ConcurrentHashMap<>();

    // Коэффициент плавности (0.1 - очень плавно, 1.0 - мгновенно)
    private static final double SMOOTHING_FACTOR = 0.3;
    private final VTSAuthenticator authenticator = new VTSAuthenticator();
    private boolean isAuthenticated = false;

    private VTSAuthListener authListener;

    private final Gson gson = new Gson();

    public VTubeStudioClient(URI serverUri) {
        super(serverUri);
    }

    public boolean isAuthenticated(){
        return this.isAuthenticated;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        VTSLogger.info("Подключено! Проверка авторизации...");
        if (authenticator.hasToken()) {
            this.send(authenticator.buildAuthRequestJson());
        } else {
            VTSLogger.info("Токена нет. Запрашиваю доступ в VTube Studio...");
            this.send(authenticator.buildTokenRequestJson());
        }
    }

    public void setAuthListener(VTSAuthListener listener) {
        this.authListener = listener;
    }


    @Override
    public void onMessage(String message) {
        // 1. Сначала ВСЕГДА логируем сырой ответ, чтобы видеть, что вообще пришло
        // VTSLogger.logResponse("Raw", message);

        try {
            JsonObject response = gson.fromJson(message, JsonObject.class);
            String messageType = response.get("messageType").getAsString();

            // 2. Теперь логируем уже распарсенный тип
//            VTSLogger.logResponse(messageType, message);

            if (messageType.equals("AuthenticationTokenResponse")) {
                String token = response.getAsJsonObject("data").get("authenticationToken").getAsString();
                authenticator.saveToken(token);
                this.send(authenticator.buildAuthRequestJson());
            }

            if (messageType.equals("AuthenticationResponse")) {
                JsonObject data = response.getAsJsonObject("data");
                if (data != null && data.has("authenticated")) {
                    this.isAuthenticated = data.get("authenticated").getAsBoolean();
                    if (this.isAuthenticated && authListener != null) {
                        authListener.onAuthSuccess();
                    }
                }
            }

            // Ответ на инъекцию параметров (эмоции)
//            if (messageType.equals("InjectParameterDataResponse")) {
//                VTSLogger.info("VTS подтвердил получение весов.");
//            }

        } catch (Exception e) {
            VTSLogger.error("Ошибка при обработке сообщения от VTS: " + message, e);
        }
    }


    /**
     * Метод для отправки весов.
     * Теперь он поддерживает фильтрацию неизвестных параметров, чтобы не спамить в API.
     */
    public void sendWeights(Map<String, Double> targetWeights) {
        if (!isAuthenticated) {
            VTSLogger.error("Попытка отправить веса без авторизации!", null);
            return;
        }
        if (!this.isOpen() || targetWeights == null || targetWeights.isEmpty()) return;

        JsonObject request = new JsonObject();
        request.addProperty("apiName", "VTubeStudioPublicAPI");
        request.addProperty("apiVersion", "1.0");
        request.addProperty("requestID", "InjectParams");
        request.addProperty("messageType", "InjectParameterDataRequest");

        JsonObject data = new JsonObject();
        data.addProperty("faceFound", true);
        data.addProperty("mode", "set");

        JsonArray parameterValues = new JsonArray();

        targetWeights.forEach((key, targetValue) -> {
            String vtsParamId = mapParamName(key);

            if (!vtsParamId.equals("UnknownParam")) {
                // --- ЛОГИКА ПОДМЕШИВАНИЯ (LERP) ---
                // Получаем предыдущее значение или 0.0 (нейтраль)
                double currentValue = lastSentValues.getOrDefault(vtsParamId, 0.0);

                // Формула: Текущее + (Цель - Текущее) * Коэффициент
                double blendedValue = currentValue + (targetValue - currentValue) * SMOOTHING_FACTOR;

                // Сохраняем для следующего шага
                lastSentValues.put(vtsParamId, blendedValue);

                JsonObject param = new JsonObject();
                param.addProperty("id", vtsParamId);
                param.addProperty("value", blendedValue);
                param.addProperty("weight", 1.0); // Полное влияние
                parameterValues.add(param);
            }
        });

        if (parameterValues.size() == 0) return;

        data.add("parameterValues", parameterValues);
        request.add("data", data);

        this.send(gson.toJson(request));
    }

    /**
     * Маппинг имен. Теперь это единая точка правды для
     * генератора шума и для нейросети.
     */
    private String mapParamName(String key) {

        return switch (key) {
            // --- Эмоции и лицо (управляет Нина) ---
            case "eye_s"      -> "ParamEyeSquint";
            // --- Взгляд и глаза (общий ресурс) ---
            case "gaze_x", "ParamEyeBallX" -> "ParamEyeBallX";
            case "gaze_y", "ParamEyeBallY" -> "ParamEyeBallY";
            case "eye_open_l" -> "ParamEyeOpenL";
            case "eye_open_r" -> "ParamEyeOpenR";

            // --- Физика и углы (управляет генератор или Нина) ---
            case "angle_x", "ParamAngleX" -> "ParamAngleX";
            case "angle_y", "ParamAngleY" -> "ParamAngleY";
            case "angle_z", "ParamAngleZ" -> "ParamAngleZ";
            case "body_x", "ParamBodyAngleX" -> "ParamBodyAngleX";

            // Позволяем напрямую передавать ID, если они уже в формате VTS
            case "ParamEyeOpenL"  -> "ParamEyeOpenL";
            case "ParamEyeOpenR"  -> "ParamEyeOpenR";
            case "ParamMouthOpen" -> "ParamMouthOpen";

            default -> {
                // Логируем только один раз или в дебаг-режиме, чтобы не забивать консоль
                yield "UnknownParam";
            }
        };
    }


    // Метод для переключения сохраненных выражений (shy, angry, suspicious)
    public void sendExpression(String expressionName, boolean active) {
        if (!isAuthenticated() || !this.isOpen()) return;

        JsonObject request = new JsonObject();
        request.addProperty("apiName", "VTubeStudioPublicAPI");
        request.addProperty("apiVersion", "1.0");
        request.addProperty("requestID", "ActivateExp");
        request.addProperty("messageType", "ExpressionActivationRequest");

        JsonObject data = new JsonObject();
        data.addProperty("expressionFile", expressionName + ".exp3.json");
        data.addProperty("active", active);

        request.add("data", data);
        this.send(this.gson.toJson(request));
    }




    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("Соединение закрыто: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }

    public void setTargetPosition(String param, double value) {
        String id = mapParamName(param);
        if (!id.equals("UnknownParam")) {
            targetPositions.put(id, value);
        }
    }

    public void tickAndSend(Map<String, Double> currentNoise) {
        if (!isAuthenticated || !this.isOpen()) return;

        JsonArray parameterValues = new JsonArray();

        // Получаем список всех активных параметров из шума и целей
        Set<String> allParams = new HashSet<>(targetPositions.keySet());
        allParams.addAll(currentNoise.keySet());

        for (String id : allParams) {
            String vtsId = mapParamName(id);

            // 1. ЛОГИКА МЯГКОГО СЛЕДОВАНИЯ (Base Movement)
            double target = targetPositions.getOrDefault(vtsId, 0.0);
            double currentBase = currentBasePositions.getOrDefault(vtsId, 0.0);

            // Плавно подтягиваем базу к цели (интерполяция)
            // Если ты поставил цель 0.8, база поползет к 0.8.
            // Если ты обнулил цель, база сама мягко поползет к 0.0.
            double nextBase = currentBase + (target - currentBase) * RETURN_SMOOTHNESS;
            currentBasePositions.put(vtsId, nextBase);

            // 2. НАЛОЖЕНИЕ ШУМА
            double noise = currentNoise.getOrDefault(id, 0.0);

            // Итоговое значение = плавающая база + шум
            double finalValue = nextBase + noise;

            // Ограничение
            finalValue = Math.max(-1.0, Math.min(1.0, finalValue));

            JsonObject param = new JsonObject();
            param.addProperty("id", vtsId);
            param.addProperty("value", finalValue);
            param.addProperty("weight", 1.0);
            parameterValues.add(param);
        }

        if (parameterValues.size() > 0) {
            sendRawInjectRequest(parameterValues);
        }
    }
    private void sendRawInjectRequest(JsonArray parameterValues) {
        JsonObject request = new JsonObject();
        request.addProperty("apiName", "VTubeStudioPublicAPI");
        request.addProperty("apiVersion", "1.0");
        request.addProperty("requestID", "InjectParamsTick");
        request.addProperty("messageType", "InjectParameterDataRequest");

        JsonObject data = new JsonObject();
        data.addProperty("faceFound", true);
        data.addProperty("mode", "set");
        data.add("parameterValues", parameterValues);

        request.add("data", data);
        this.send(gson.toJson(request));
    }
}