package org.example.db.entity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Модель воспоминания (memory)
 * Хранится в Qdrant вместе с эмбеддингом
 */
public class Memory {
    private String id;                          // UUID уникальный идентификатор
    private String content;                     // Текст воспоминания
    private List<Float> embedding;              // Вектор эмбеддинга (384-мерный для Ollama)
    private LocalDateTime createdAt;            // Когда создано воспоминание
    private LocalDateTime lastTriggeredAt;      // Когда последний раз вспомнили
    private LocalDateTime summarizedAt;         // Когда суммаризировано (null = не суммаризировано)
    private MemoryState state;                  // FRESH, SUMMARIZED, FORGOTTEN
    private List<Long> relatedNumericIds;       // Связанные числовые параметры
    private double importance;                  // 0.0-1.0 важность воспоминания

    public enum MemoryState {
        FRESH,          // Свежее, в чистом виде (< 1 дня)
        SUMMARIZED,     // Суммаризировано (1-2 дня)
        FORGOTTEN       // Забыто (> 2 дней или при перегрузке)
    }

    public Memory() {}

    public Memory(String id, String content, List<Float> embedding) {
        this.id = id;
        this.content = content;
        this.embedding = embedding;
        this.createdAt = LocalDateTime.now();
        this.lastTriggeredAt = LocalDateTime.now();
        this.state = MemoryState.FRESH;
        this.importance = 0.5;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<Float> getEmbedding() { return embedding; }
    public void setEmbedding(List<Float> embedding) { this.embedding = embedding; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastTriggeredAt() { return lastTriggeredAt; }
    public void setLastTriggeredAt(LocalDateTime lastTriggeredAt) { this.lastTriggeredAt = lastTriggeredAt; }

    public LocalDateTime getSummarizedAt() { return summarizedAt; }
    public void setSummarizedAt(LocalDateTime summarizedAt) { this.summarizedAt = summarizedAt; }

    public MemoryState getState() { return state; }
    public void setState(MemoryState state) { this.state = state; }

    public List<Long> getRelatedNumericIds() { return relatedNumericIds; }
    public void setRelatedNumericIds(List<Long> relatedNumericIds) { this.relatedNumericIds = relatedNumericIds; }

    public double getImportance() { return importance; }
    public void setImportance(double importance) { this.importance = importance; }
}

