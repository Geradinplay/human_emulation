package org.example.vts.auth.param;

import com.google.gson.JsonObject;
import org.example.vts.client.VTubeStudioClient;
import org.example.vts.log.VTSLogger;


public class VTSParameterManager {
    private final VTubeStudioClient client;

    public VTSParameterManager(VTubeStudioClient client) {
        this.client = client;
    }

    /**
     * Регистрация всех параметров.
     * Запускай этот метод ОДИН РАЗ после успешной авторизации.
     */
    public void registerAll() {
        VTSLogger.info("Регистрация параметров... Поехали!");

        // --- ГЛАЗА (Диапазон -1..1 идеален для зрачков) ---
        createParam("ParamEyeBallX", "Взгляд влево-вправо", -1, 1, 0);
        createParam("ParamEyeBallY", "Взгляд вверх-вниз", -1, 1, 0);

        // --- ЭМОЦИИ ---
        createParam("ParamExtraBlush", "Румянец", 0, 1, 0);
        createParam("ParamBrowInnerUp", "Брови (Эмоции)", -1, 1, 0);

        // --- ДВИЖЕНИЯ ГОЛОВЫ ---
        // Ставим -15..15, чтобы твои текущие формулы в генераторе (где амплитуда ~10)
        // заставляли голову поворачиваться почти до упора.
        createParam("ParamAngleX", "Голова: Поворот (X)", -15, 15, 0);
        createParam("ParamAngleY", "Голова: Наклон (Y)", -15, 15, 0);
        createParam("ParamAngleZ", "Голова: Крен (Z)", -15, 15, 0);

        // --- ДВИЖЕНИЯ ТЕЛА ---
        createParam("ParamBodyAngleX", "Тело: Поворот (X)", -10, 10, 0);
    }

    private void createParam(String name, String desc, double min, double max, double def) {
        JsonObject request = new JsonObject();
        request.addProperty("apiName", "VTubeStudioPublicAPI");
        request.addProperty("apiVersion", "1.0");
        request.addProperty("requestID", "Create_" + name);
        request.addProperty("messageType", "ParameterCreationRequest");

        JsonObject data = new JsonObject();
        data.addProperty("parameterName", name);
        data.addProperty("explanation", desc);
        data.addProperty("min", min);
        data.addProperty("max", max);
        data.addProperty("defaultValue", def);

        request.add("data", data);
        client.send(request.toString());
    }
}