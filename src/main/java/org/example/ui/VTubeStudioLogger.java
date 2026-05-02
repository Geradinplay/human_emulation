package org.example.ui;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Логирование событий VTube Studio
 */
public class VTubeStudioLogger {
    private PrintWriter logWriter;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private StringBuilder screenLog = new StringBuilder();
    private static final int MAX_SCREEN_LOG_LINES = 1000;

    public VTubeStudioLogger(String logFilePath) {
        try {
            logWriter = new PrintWriter(new FileWriter(logFilePath, true), true);
        } catch (IOException e) {
            System.err.println("❌ Не удалось открыть логирование: " + e.getMessage());
        }
    }

    /**
     * Логирование события
     */
    public void log(String event, String message) {
        String timestamp = dateFormat.format(new Date());
        String logLine = String.format("[%s] %s: %s", timestamp, event, message);

        // Запись в файл
        if (logWriter != null) {
            logWriter.println(logLine);
        }

        // Добавление в экранный лог
        addToScreenLog(logLine);

        // Вывод в консоль
        System.out.println(logLine);
    }

    /**
     * Логирование отправки параметров
     */
    public void logParameterSent(String paramId, Double value) {
        log("PARAM_SENT", String.format("ID=%s, Value=%.2f", paramId, value));
    }

    /**
     * Логирование отправки эмоции
     */
    public void logEmotionSent(String emotion, String parameters) {
        log("EMOTION_SENT", String.format("Emotion=%s, Params=%s", emotion, parameters));
    }

    /**
     * Логирование ошибки
     */
    public void logError(String errorMessage) {
        log("ERROR", errorMessage);
    }

    /**
     * Логирование информационного сообщения
     */
    public void logInfo(String info) {
        log("INFO", info);
    }

    /**
     * Логирование подключения
     */
    public void logConnection(String message) {
        log("CONNECTION", message);
    }

    /**
     * Добавить в экранный лог
     */
    private void addToScreenLog(String line) {
        screenLog.append(line).append("\n");

        // Ограничиваем размер лога в памяти
        String[] lines = screenLog.toString().split("\n");
        if (lines.length > MAX_SCREEN_LOG_LINES) {
            screenLog = new StringBuilder();
            for (int i = lines.length - MAX_SCREEN_LOG_LINES; i < lines.length; i++) {
                if (i >= 0) {
                    screenLog.append(lines[i]).append("\n");
                }
            }
        }
    }

    /**
     * Получить экранный лог
     */
    public String getScreenLog() {
        return screenLog.toString();
    }

    /**
     * Очистить экранный лог
     */
    public void clearScreenLog() {
        screenLog = new StringBuilder();
    }

    /**
     * Закрыть логирование
     */
    public void close() {
        if (logWriter != null) {
            logWriter.close();
        }
    }
}

