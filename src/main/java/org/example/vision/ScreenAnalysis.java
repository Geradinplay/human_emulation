package org.example.vision;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Результат анализа скриншота
 * Содержит информацию о том, что видит Moondream и нужна ли Нине реакция
 */
public class ScreenAnalysis {

    private final long timestamp;
    private final String screenshotPath;
    private final String description;
    private boolean shouldComment = false;
    private double confidenceScore = 0.0;
    private List<String> detectedEvents = new ArrayList<>();

    public ScreenAnalysis(long timestamp, String screenshotPath, String description) {
        this.timestamp = timestamp;
        this.screenshotPath = screenshotPath;
        this.description = description;
        this.detectedEvents = extractEvents(description);
    }

    /**
     * Извлекает ключевые события из описания
     */
    private List<String> extractEvents(String description) {
        List<String> events = new ArrayList<>();

        if (description == null || description.isEmpty()) {
            return events;
        }

        String lowerDesc = description.toLowerCase();

        // Проверяем различные типы событий
        if (hasPattern(lowerDesc, "ошибка|error|exception|failed|не работает")) {
            events.add("ERROR");
        }
        if (hasPattern(lowerDesc, "исключение|crashes|crash|停止")) {
            events.add("CRASH");
        }
        if (hasPattern(lowerDesc, "предупреждение|warning|важно|внимание")) {
            events.add("WARNING");
        }
        if (hasPattern(lowerDesc, "смешно|funny|lol|забавно|комич")) {
            events.add("HUMOR");
        }
        if (hasPattern(lowerDesc, "необычно|странно|странный|unusual|weird|странная")) {
            events.add("ANOMALY");
        }
        if (hasPattern(lowerDesc, "видео|video|playing|проигрыва")) {
            events.add("VIDEO");
        }
        if (hasPattern(lowerDesc, "текст|текстов|печать|typing|пишет")) {
            events.add("TYPING");
        }
        if (hasPattern(lowerDesc, "код|code|программ|program")) {
            events.add("CODE");
        }

        return events;
    }

    private boolean hasPattern(String text, String patterns) {
        String[] patternArray = patterns.split("\\|");
        for (String pattern : patternArray) {
            if (text.contains(pattern.toLowerCase().trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Проверяет, есть ли аномалии на экране
     */
    public boolean hasAnomalies() {
        return detectedEvents.contains("ERROR") ||
               detectedEvents.contains("CRASH") ||
               detectedEvents.contains("ANOMALY");
    }

    /**
     * Проверяет, есть ли что-то смешное
     */
    public boolean hasHumor() {
        return detectedEvents.contains("HUMOR");
    }

    /**
     * Проверяет, происходит ли активное действие пользователя
     */
    public boolean hasUserAction() {
        return detectedEvents.contains("TYPING") ||
               detectedEvents.contains("CODE") ||
               detectedEvents.contains("VIDEO");
    }

    /**
     * Возвращает описание ключевых событий
     */
    public String getEventsSummary() {
        if (detectedEvents.isEmpty()) {
            return "Обычный скриншот";
        }
        return String.join(", ", detectedEvents);
    }

    // Getters & Setters
    public long getTimestamp() {
        return timestamp;
    }

    public String getScreenshotPath() {
        return screenshotPath;
    }

    public String getDescription() {
        return description;
    }

    public boolean isShouldComment() {
        return shouldComment;
    }

    public void setShouldComment(boolean shouldComment) {
        this.shouldComment = shouldComment;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public List<String> getDetectedEvents() {
        return detectedEvents;
    }

    @Override
    public String toString() {
        return "ScreenAnalysis{" +
                "timestamp=" + timestamp +
                ", screenshotPath='" + screenshotPath + '\'' +
                ", shouldComment=" + shouldComment +
                ", events=" + getEventsSummary() +
                ", description='" + description.substring(0, Math.min(50, description.length())) + "...'" +
                '}';
    }
}

