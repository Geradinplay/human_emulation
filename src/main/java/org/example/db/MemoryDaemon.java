package org.example.db;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.*;

/**
 * Фоновый демон для управления воспоминаниями
 * Выполняет периодическую суммаризацию и забывание
 */
public class MemoryDaemon {
    private final MemoryManager memoryManager;
    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> summarizationTask;
    private ScheduledFuture<?> forgetTask;
    private LocalDateTime lastSummarization;
    private LocalDateTime lastForgetProcedure;

    public MemoryDaemon(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
        this.executor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "MemoryDaemon-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
        this.lastSummarization = LocalDateTime.now();
        this.lastForgetProcedure = LocalDateTime.now();
    }

    /**
     * Запускает фоновый демон
     */
    public void start() {
        // Суммаризация каждый день в полночь
        summarizationTask = executor.scheduleAtFixedRate(
                this::runSummarizationIfNeeded,
                getDelayUntilMidnight(),
                24,  // Каждые 24 часа
                TimeUnit.HOURS
        );

        // Проверка забывания каждый час
        forgetTask = executor.scheduleAtFixedRate(
                this::runForgetProcedureIfNeeded,
                1,
                1,   // Каждый час
                TimeUnit.HOURS
        );
    }

    /**
     * Останавливает демон
     */
    public void stop() {
        if (summarizationTask != null) {
            summarizationTask.cancel(false);
        }
        if (forgetTask != null) {
            forgetTask.cancel(false);
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Выполняет суммаризацию если нужно
     */
    private void runSummarizationIfNeeded() {
        try {
            long hoursSinceLastSummarization = ChronoUnit.HOURS.between(
                    lastSummarization,
                    LocalDateTime.now()
            );

            // Суммаризация раз в день
            if (hoursSinceLastSummarization >= 24) {
                System.out.println("\n⏱️  Время суммаризации!");
                memoryManager.runDailySummarization();
                lastSummarization = LocalDateTime.now();
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка в демоне суммаризации: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Выполняет забывание если нужно
     */
    private void runForgetProcedureIfNeeded() {
        try {
            long hoursSinceLastForget = ChronoUnit.HOURS.between(
                    lastForgetProcedure,
                    LocalDateTime.now()
            );

            // Забывание раз в час
            if (hoursSinceLastForget >= 1) {
                System.out.println("\n⏱️  Время забывания!");
                memoryManager.runForgetProcedure();
                lastForgetProcedure = LocalDateTime.now();
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка в демоне забывания: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Вычисляет задержку до следующей полуночи
     */
    private long getDelayUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().atStartOfDay().plusDays(1);
        long secondsUntilMidnight = ChronoUnit.SECONDS.between(now, midnight);
        return Math.max(1, secondsUntilMidnight / 3600); // Конвертируем в часы
    }

    /**
     * Принудительно запустить суммаризацию (например, при перегрузке контекста)
     */
    public void forceEmergencySummarization() throws Exception {
        System.out.println("\n🚨 ЭКСТРЕННАЯ СУММАРИЗАЦИЯ (перегрузка контекста)");
        memoryManager.runDailySummarization();
        lastSummarization = LocalDateTime.now();
    }

    /**
     * Принудительно запустить забывание (например, при критической перегрузке)
     */
    public void forceEmergencyForget() throws Exception {
        System.out.println("\n🚨 ЭКСТРЕННОЕ ЗАБЫВАНИЕ (критическая перегрузка)");
        memoryManager.runForgetProcedure();
        lastForgetProcedure = LocalDateTime.now();
    }

    /**
     * Проверяет жива ли демон
     */
    public boolean isRunning() {
        return !executor.isShutdown() &&
               (summarizationTask == null || !summarizationTask.isDone()) &&
               (forgetTask == null || !forgetTask.isDone());
    }
}

