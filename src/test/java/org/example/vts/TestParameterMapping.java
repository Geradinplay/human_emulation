package org.example.vts;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Тест для проверки маппинга параметров
 */
public class TestParameterMapping {
    public static void main(String[] args) {
        // Включаем логирование
        Logger.getGlobal().setLevel(Level.ALL);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        Logger.getLogger("").addHandler(handler);

        ExpressionHandler handler1 = new ExpressionHandler();

        System.out.println("=== ТЕСТИРОВАНИЕ МАППИНГА ПАРАМЕТРОВ ===\n");

        // Тест 1: Простые параметры
        testResponse("[WEIGHTS: blush:0.5, smile:-0.3, angry:-0.4] Привет!", handler1);

        // Тест 2: С параметрами движения (gaze)
        testResponse("[WEIGHTS: gaze_x:0.7, gaze_y:-0.5, blush:0.8] Смотрю на тебя!", handler1);

        // Тест 3: С параметром eye_s
        testResponse("[WEIGHTS: eye_s:0.9, blush:0.5] Открываю глаза пошире!", handler1);

        // Тест 4: Полный набор параметров
        testResponse("[WEIGHTS: blush:0.5, smile:-0.3, angry:-0.4, gaze_x:0.7, gaze_y:-0.2, eye_s:0.8, head_x:0.3] Полный ответ!", handler1);

        System.out.println("\n✅ Тестирование завершено!");
    }

    private static void testResponse(String response, ExpressionHandler handler) {
        System.out.println("📝 Текст ответа: " + response);
        InjectParameterDataRequest request = handler.extractEmotionsAndBuildRequest(response);

        if (request != null) {
            System.out.println("✅ Параметры найдены:");
            System.out.println("   Количество: " + request.getParameterValues().size());
            for (var param : request.getParameterValues()) {
                System.out.println("   - " + param.getId() + " = " + param.getValue());
            }
        } else {
            System.out.println("❌ Параметры не найдены!");
        }
        System.out.println();
    }
}

