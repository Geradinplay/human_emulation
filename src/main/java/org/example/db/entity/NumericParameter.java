package org.example.db.entity;

import java.time.LocalDateTime;

/**
 * Модель числовых параметров
 * Хранится отдельно от воспоминаний для CASCADE удаления
 */
public class NumericParameter {
    private long id;                    // ID параметра
    private String memoryId;            // ID связанного воспоминания
    private double value;               // Числовое значение
    private String parameterName;       // Название параметра (e.g., "quantity", "price")
    private String unit;                // Единица измерения (e.g., "шт", "руб")
    private LocalDateTime createdAt;    // Когда добавлено
    private boolean isWildcard;         // true = это дженерик значение, используется в нескольких воспоминаниях

    public NumericParameter() {}

    public NumericParameter(long id, String memoryId, double value, String parameterName, String unit) {
        this.id = id;
        this.memoryId = memoryId;
        this.value = value;
        this.parameterName = parameterName;
        this.unit = unit;
        this.createdAt = LocalDateTime.now();
        this.isWildcard = false;
    }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getMemoryId() { return memoryId; }
    public void setMemoryId(String memoryId) { this.memoryId = memoryId; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    public String getParameterName() { return parameterName; }
    public void setParameterName(String parameterName) { this.parameterName = parameterName; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isWildcard() { return isWildcard; }
    public void setWildcard(boolean wildcard) { isWildcard = wildcard; }
}

