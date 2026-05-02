package org.example.vision;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Событийный фильтр для Нины
 * Решает, когда Нина должна комментировать экран, а когда молчать
 *
 * Логика:
 * - НЕ каждый скриншот требует комментария
 * - Нина говорит только когда это интересно/важно
 * - Также может внезапно захотеть что-то сказать (chaos factor)
 */
public class EventFilter {
    private static final Logger LOG = Logger.getLogger(EventFilter.class.getName());

    // Времена и счётчики
    private long lastCommentTime = 0;
    private int commentCount = 0;
    private int skippedCount = 0;

    // Конфигурация фильтра
    private static final long MIN_TIME_BETWEEN_COMMENTS = 30_000;  // 30 сек минимум между комментариями
    private static final long MAX_SILENT_TIME = 120_000;           // Максимум 2 мин молчания
    private static final int COMMENT_SPAM_THRESHOLD = 5;            // Макс 5 комментов за окно
    private static final double RANDOM_COMMENT_PROBABILITY = 0.15;  // 15% шанс спонтанного комментария

    // Окно времени для подсчёта комментов
    private LinkedList<Long> commentTimestamps = new LinkedList<>();

    public EventFilter() {
        LOG.log(Level.INFO, "🎭 Инициализирован Событийный фильтр для Нины");
    }

    /**
     * Основное решение: должна ли Нина комментировать этот скриншот?
     */
    public boolean shouldNinaComment(ScreenAnalysis analysis) {
        long now = System.currentTimeMillis();

        // ========== ПРАВИЛО 1: КРИТИЧНЫЕ СОБЫТИЯ ==========
        // Нина ВСЕГДА реагирует на ошибки и аномалии
        if (analysis.hasAnomalies()) {
            LOG.log(Level.INFO, "🚨 КРИТИЧНОЕ СОБЫТИЕ: " + analysis.getEventsSummary());
            recordComment(now);
            return true;
        }

        // ========== ПРАВИЛО 2: СМЕШНЫЕ МОМЕНТЫ ==========
        // Нина не может удержаться от смешного
        if (analysis.hasHumor()) {
            LOG.log(Level.INFO, "😆 СМЕШНО: " + analysis.getEventsSummary());
            recordComment(now);
            return true;
        }

        // ========== ПРАВИЛО 3: МИНИМАЛЬНЫЙ ИНТЕРВАЛ ==========
        // Не спамим комментариями - не менее 30 сек между ними
        long timeSinceLastComment = now - lastCommentTime;
        if (timeSinceLastComment < MIN_TIME_BETWEEN_COMMENTS) {
            LOG.log(Level.FINEST, "⏱️ Слишком рано - последний комментарий " +
                    timeSinceLastComment + " мс назад");
            skippedCount++;
            return false;
        }

        // ========== ПРАВИЛО 4: ПРЕДОТВРАЩЕНИЕ МОЛЧАНИЯ ==========
        // Если слишком долго молчит, нужно что-то сказать
        if (timeSinceLastComment > MAX_SILENT_TIME) {
            LOG.log(Level.INFO, "🤐 СЛИШКОМ ДОЛГО МОЛЧИТ - принудительный комментарий!");
            recordComment(now);
            return true;
        }

        // ========== ПРАВИЛО 5: ХАОС И СПОНТАННОСТЬ ==========
        // Нина может внезапно захотеть что-то сказать (Tsundere chaos)
        if (Math.random() < RANDOM_COMMENT_PROBABILITY) {
            LOG.log(Level.INFO, "💭 СПОНТАННЫЙ МОМЕНТ: Нина хочет что-то сказать!");
            recordComment(now);
            return true;
        }

        // ========== ПРАВИЛО 6: ИНТЕРЕСНЫЕ ДЕЙСТВИЯ ==========
        // Если пользователь делает что-то интересное, но не критичное
        if (analysis.hasUserAction() && isInterestingAction(analysis)) {
            // Берём в учёт спектр интереса
            if (shouldCommentOnAction(analysis, now)) {
                LOG.log(Level.INFO, "👀 ИНТЕРЕСНОЕ ДЕЙСТВИЕ: " + analysis.getEventsSummary());
                recordComment(now);
                return true;
            }
        }

        // ========== ПРАВИЛО 7: ПРОВЕРКА СПАМА ==========
        // Если уже много комментов подряд - заставляем помолчать
        if (isCommentingTooMuch()) {
            LOG.log(Level.FINEST, "🤫 Нина много говорит - давайте ей помолчать");
            return false;
        }

        // Ничего интересного - молчим
        LOG.log(Level.FINEST, "😑 Обычный момент - молчим");
        skippedCount++;
        return false;
    }

    /**
     * Проверяет, интересное ли действие
     */
    private boolean isInterestingAction(ScreenAnalysis analysis) {
        List<String> events = analysis.getDetectedEvents();

        // Видео всегда интересно комментировать
        if (events.contains("VIDEO")) {
            return true;
        }

        // Код может быть интересным (но не всегда)
        if (events.contains("CODE")) {
            // 30% шанс отреагировать на код
            return Math.random() < 0.3;
        }

        // Печать сообщений может быть интересна
        if (events.contains("TYPING")) {
            // 20% шанс отреагировать на печать
            return Math.random() < 0.2;
        }

        return false;
    }

    /**
     * Решает, комментировать ли действие с учётом времени
     */
    private boolean shouldCommentOnAction(ScreenAnalysis analysis, long now) {
        long timeSinceLastComment = now - lastCommentTime;

        // Если давно не говорила - может пропустить
        if (timeSinceLastComment > 60_000) {
            return true;
        }

        // Иначе нужен больший интервал
        return timeSinceLastComment > MIN_TIME_BETWEEN_COMMENTS;
    }

    /**
     * Проверяет, не спамит ли Нина комментариями
     */
    private boolean isCommentingTooMuch() {
        // Очищаем старые комментарии (старше 1 минуты)
        long oneMinuteAgo = System.currentTimeMillis() - 60_000;
        commentTimestamps.removeIf(ts -> ts < oneMinuteAgo);

        // Если более 5 комментов в минуту - это спам
        return commentTimestamps.size() >= COMMENT_SPAM_THRESHOLD;
    }

    /**
     * Записывает момент комментария
     */
    private void recordComment(long timestamp) {
        lastCommentTime = timestamp;
        commentCount++;
        commentTimestamps.addLast(timestamp);
        LOG.log(Level.INFO, "📝 Комментарий #" + commentCount + " | Пропущено: " + skippedCount);
    }

    /**
     * Возвращает статистику фильтра
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalComments", commentCount);
        stats.put("skippedEvents", skippedCount);
        stats.put("recentCommentRate", commentTimestamps.size());
        stats.put("timeSinceLastComment", System.currentTimeMillis() - lastCommentTime);
        return stats;
    }

    /**
     * Сброс статистики (полезно для тестирования)
     */
    public void reset() {
        lastCommentTime = 0;
        commentCount = 0;
        skippedCount = 0;
        commentTimestamps.clear();
        LOG.log(Level.INFO, "🔄 Событийный фильтр сброшен");
    }
}

