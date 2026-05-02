package org.example.vts;



import org.example.vts.auth.VTSAuthListener;
import org.example.vts.auth.param.VTSParameterManager;
import org.example.vts.client.ExpressionHandlerStub;
import org.example.vts.client.VTubeStudioClient;
import org.example.vts.context.VTSContext;
import org.example.vts.log.VTSLogger;
import org.example.vts.manager.VTSMovementManager;


import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

public class VTSLauncher implements VTSAuthListener {
    private final VTubeStudioClient client;
    private final VTSMovementManager movementManager;
    private final VTSParameterManager parameterManager; // Добавляем поле

    // Добавь это в конец класса VTSLauncher
    public VTSMovementManager getMovementManager() {
        return movementManager;
    }
    public VTSLauncher(URI uri) throws URISyntaxException {
        this.client = new VTubeStudioClient(uri);
        VTSContext.setClient(this.client);
        this.client.setAuthListener(this);
        this.movementManager = new VTSMovementManager(client);
        // Инициализируем менеджер параметров
        this.parameterManager = new VTSParameterManager(client);
    }

    public void start() {
        client.connect();
    }

    public VTubeStudioClient getClient() {
        return client;
    }


    /**
     * Мгновенный сброс всех параметров в 0
     */
    public void resetAllEmotions() {
        VTSLogger.info("Кнопка паники: Мгновенный принудительный сброс.");
        Map<String, Double> reset = Map.of(
                "blush", 0.0,
                "angry", 0.0,
                "gaze_x", 0.0
        );
        movementManager.setSharpness(1.0);
        movementManager.forceReset(reset); // Используем принудительный метод
    }



    @Override
    public void onAuthSuccess() {
        VTSLogger.info("Авторизация прошла успешно.");

        // 1. Сначала регистрируем параметры в студии
        parameterManager.registerAll();

        // 2. Затем запускаем поток обработки движений
        VTSLogger.info("Запускаем менеджер движений...");
        movementManager.start();
    }

    @Override
    public void onAuthFail(String reason) {
        VTSLogger.error("Ошибка авторизации: " + reason, null);
    }

    // Добавь это в VTSLauncher.java
    public void stopAll() {
        if (movementManager != null) {
            movementManager.stop(); // Останавливаем поток цикла
        }
        if (client != null) {
            client.close(); // Закрываем соединение
        }
    }

    public void triggerAnimation(String name) {
        if (movementManager != null) {
            movementManager.triggerAnimation(name);
        }
    }
    public void triggerExpression(String name) {
        if (movementManager != null) {
            movementManager.triggerExpression(name);
        }
    }

    public void triggerAnimatedExpression(String expressionName, String animationName) {
        if (movementManager != null) {
            movementManager.triggerAnimationWithExpression(animationName, expressionName);
        }
    }

    public ExpressionHandlerStub getExpressionHandler() {
        // Ошибка была здесь, если в ExpressionHandlerStub не было нужного конструктора
        return new ExpressionHandlerStub();
    }
}