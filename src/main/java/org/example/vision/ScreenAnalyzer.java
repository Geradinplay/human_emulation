package org.example.vision;



import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Анализ скриншотов через Moondream
 * Vision система для Нины (видит что на экране)
 */
public class ScreenAnalyzer {
    private static final Logger LOG = Logger.getLogger(ScreenAnalyzer.class.getName());

    private final OllamaVisionClient ollamaClient;
    private final EventFilter eventFilter;

    public ScreenAnalyzer(String ollamaBaseUrl) {
        this.ollamaClient = new OllamaVisionClient(ollamaBaseUrl);
        this.eventFilter = new EventFilter();
    }

    /**
     * Анализирует скриншот и решает, должна ли Нина его комментировать
     *
     * @param screenshotPath Путь к скриншоту
     * @return ScreenAnalysis с информацией об анализе
     */
    public ScreenAnalysis analyzeScreenshot(String screenshotPath) {
        try {
            LOG.log(Level.FINE, "📸 Анализируем скриншот: " + screenshotPath);

            // Получаем описание скриншота от Moondream
            String description = ollamaClient.analyzeImage(screenshotPath);

            // Создаём объект анализа
            ScreenAnalysis analysis = new ScreenAnalysis(
                System.currentTimeMillis(),
                screenshotPath,
                description
            );

            // Проверяем через Событийный фильтр
            boolean shouldComment = eventFilter.shouldNinaComment(analysis);
            analysis.setShouldComment(shouldComment);

            LOG.log(Level.FINE, "🔍 Анализ завершён. Комментировать: " + shouldComment);

            return analysis;

        } catch (Exception e) {
            LOG.log(Level.WARNING, "❌ Ошибка анализа скриншота: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Анализирует серию скриншотов с интервалом
     *
     * @param interval Интервал анализа в миллисекундах
     * @param callback Функция для обработки результатов
     */
    public void startContinuousAnalysis(long interval, AnalysisCallback callback) {
        Thread analysisThread = new Thread(() -> {
            while (Thread.currentThread().isAlive()) {
                try {
                    // Делаем скриншот
                    String screenshotPath = ScreenshotUtil.captureScreen();

                    // Анализируем
                    ScreenAnalysis analysis = analyzeScreenshot(screenshotPath);

                    if (analysis != null && callback != null) {
                        callback.onAnalysisComplete(analysis);
                    }

                    Thread.sleep(interval);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Ошибка в цикле анализа: " + e.getMessage());
                }
            }
        }, "ScreenAnalyzer-Thread");

        analysisThread.setDaemon(true);
        analysisThread.start();
    }

    public interface AnalysisCallback {
        void onAnalysisComplete(ScreenAnalysis analysis);
    }

    /**
     * Проверяет доступность всех необходимых сервисов
     */
    public boolean checkServicesAvailable() {
        boolean ollamaAvailable = ollamaClient.isAvailable();

        if (!ollamaAvailable) {
            LOG.log(Level.SEVERE, "❌ Ollama недоступна!");
            return false;
        }

        try {
            if (!ollamaClient.hasMoondream()) {
                LOG.log(Level.SEVERE, "❌ Модель Moondream не установлена!");
                LOG.log(Level.INFO, "Установите: ollama pull moondream");
                return false;
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "❌ Ошибка проверки Moondream: " + e.getMessage());
            return false;
        }

        LOG.log(Level.INFO, "✅ Все сервисы доступны!");
        return true;
    }
}

