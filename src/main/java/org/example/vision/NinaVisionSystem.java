package org.example.vision;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Nina Vision System - Главный оркестратор
 *
 * Цикл:
 * 1. Делаем скриншот
 * 2. Отправляем в Moondream (видит что на экране)
 * 3. Событийный фильтр решает - нужно ли комментировать
 * 4. Если да - отправляем в Stheno LLM
 * 5. Получаем комментарий от Нины
 * 6. Отправляем на VTube Studio (аватар говорит)
 */
public class NinaVisionSystem {
    private static final Logger LOG = Logger.getLogger(NinaVisionSystem.class.getName());

    private final ScreenAnalyzer screenAnalyzer;
    private final SthenoChatInterface sthenoBrain;
    private final String vtsHost;
    private final int vtsPort;

    private volatile boolean running = false;
    private Thread analysisThread;

    // Параметры
    private long analysisInterval = 5000;  // Анализируем каждые 5 секунд

    public NinaVisionSystem(
            String ollamaBaseUrl,
            String stheneBaseUrl,
            String vtsHost,
            int vtsPort) {

        this.screenAnalyzer = new ScreenAnalyzer(ollamaBaseUrl);
        this.sthenoBrain = new SthenoChatInterface(stheneBaseUrl);
        this.vtsHost = vtsHost;
        this.vtsPort = vtsPort;

        LOG.log(Level.INFO, "👁️ Инициализирована Nina Vision System");
        LOG.log(Level.INFO, "  Moondream: " + ollamaBaseUrl);
        LOG.log(Level.INFO, "  Stheno: " + stheneBaseUrl);
        LOG.log(Level.INFO, "  VTS: " + vtsHost + ":" + vtsPort);
    }

    /**
     * Запускает цикл анализа
     */
    public void start() {
        if (running) {
            LOG.log(Level.WARNING, "⚠️ Vision System уже запущена");
            return;
        }

        running = true;

        // Проверяем доступность сервисов
        if (!screenAnalyzer.checkServicesAvailable()) {
            LOG.log(Level.SEVERE, "❌ Не все сервисы доступны!");
            running = false;
            return;
        }

        // Запускаем цикл анализа
        screenAnalyzer.startContinuousAnalysis(analysisInterval, analysis -> {
            if (analysis == null) return;

            LOG.log(Level.FINE, "🔍 Анализ завершён: " + analysis.toString());

            // Отправляем в Stheno если нужен комментарий
            if (analysis.isShouldComment()) {
                String ninaComment = sthenoBrain.getScreenCommentFromNina(analysis);

                if (ninaComment != null && !ninaComment.isEmpty()) {
                    LOG.log(Level.INFO, "💬 Нина: " + ninaComment);

                    // Отправляем на VTube Studio
                    sendToVTS(ninaComment);
                }
            }
        });

        LOG.log(Level.INFO, "✅ Nina Vision System запущена!");
    }

    /**
     * Останавливает цикл анализа
     */
    public void stop() {
        running = false;
        LOG.log(Level.INFO, "⏹️ Nina Vision System остановлена");
    }

    /**
     * Отправляет комментарий на VTube Studio
     */
    private void sendToVTS(String comment) {
        try {
            // TODO: Интегрировать с VTubeStudioWebSocketClient
            LOG.log(Level.INFO, "📡 Отправляем комментарий на VTS: " + comment);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "❌ Ошибка при отправке на VTS: " + e.getMessage());
        }
    }

    /**
     * Устанавливает интервал анализа
     */
    public void setAnalysisInterval(long milliseconds) {
        this.analysisInterval = milliseconds;
        LOG.log(Level.INFO, "⚙️ Интервал анализа: " + milliseconds + " мс");
    }

    /**
     * Возвращает текущий статус
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Возвращает статистику
     */
    public void printStats() {
        LOG.log(Level.INFO, "📊 Статистика Vision System:");
        LOG.log(Level.INFO, "  Статус: " + (running ? "🟢 Работает" : "🔴 Остановлена"));
        LOG.log(Level.INFO, "  Интервал анализа: " + analysisInterval + " мс");
        // TODO: Добавить более подробную статистику
    }
}

