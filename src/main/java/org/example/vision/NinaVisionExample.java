package org.example.vision;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Пример использования Nina Vision System
 */
public class NinaVisionExample {
    private static final Logger LOG = Logger.getLogger(NinaVisionExample.class.getName());

    public static void main(String[] args) {
        LOG.log(Level.INFO, "🚀 Запуск примера Nina Vision System");
        LOG.log(Level.INFO, "────────────────────────────────────────");

        try {
            // ========== КОНФИГУРАЦИЯ ==========
            String ollamaUrl = "http://localhost:11434";
            String stheneUrl = "http://localhost:5000";
            String vtsHost = "localhost";
            int vtsPort = 8001;

            // ========== ИНИЦИАЛИЗАЦИЯ ==========
            LOG.log(Level.INFO, "📡 Инициализируем Nina Vision System...");
            NinaVisionSystem visionSystem = new NinaVisionSystem(
                ollamaUrl,
                stheneUrl,
                vtsHost,
                vtsPort
            );

            // ========== НАСТРОЙКА ==========
            // Анализируем скриншот каждые 5 секунд
            visionSystem.setAnalysisInterval(5000);

            // ========== ЗАПУСК ==========
            LOG.log(Level.INFO, "▶️ Запускаем цикл анализа...");
            visionSystem.start();

            // ========== РАБОТА ==========
            LOG.log(Level.INFO, "👁️ Nina следит за экраном...");
            LOG.log(Level.INFO, "📝 Нина будет комментировать интересные моменты");
            LOG.log(Level.INFO, "════════════════════════════════════════");

            // Слушаем Ctrl+C для остановки
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOG.log(Level.INFO, "⏹️ Получена команда завершения");
                visionSystem.stop();
                LOG.log(Level.INFO, "✅ Nina Vision System выключена");
            }));

            // Периодически выводим статистику
            while (visionSystem.isRunning()) {
                Thread.sleep(60000); // Каждую минуту
                visionSystem.printStats();
            }

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "❌ Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

