package org.example.db;

import org.example.AvatarBrain;
import org.example.db.entity.Memory;
import org.example.db.entity.NumericParameter;
import org.example.db.service.EmbeddingService;
import org.example.db.service.QdrantMemoryService;

import java.util.*;
import java.util.concurrent.*;

/**
 * Менеджер памяти - управляет жизненным циклом воспоминаний
 * Отвечает за суммаризацию и забывание
 */
public class MemoryManager {
    private final QdrantMemoryService qdrantService;
    private final EmbeddingService embeddingService;
    private final AvatarBrain brain;
    private final Map<Long, NumericParameter> numericStorage; // In-memory хранилище чисел
    private long nextNumericId = 1;
    private final Map<Long, Long> numericAccessTime; // Время последнего доступа для отслеживания

    public MemoryManager(QdrantMemoryService qdrantService, EmbeddingService embeddingService, AvatarBrain brain) {
        this.qdrantService = qdrantService;
        this.embeddingService = embeddingService;
        this.brain = brain;
        this.numericStorage = new ConcurrentHashMap<>();
        this.numericAccessTime = new ConcurrentHashMap<>();
    }

    /**
     * Создаёт новое воспоминание из текста
     */
    public String createMemory(String content, List<String> numericParameters) throws Exception {
        // Генерируем эмбеддинг
        List<Float> embedding = embeddingService.generateEmbedding(content);

        // Сохраняем числовые параметры
        List<Long> numericIds = new ArrayList<>();
        if (numericParameters != null) {
            for (String param : numericParameters) {
                long numId = addNumericParameter(content, param);
                numericIds.add(numId);
            }
        }

        // Добавляем в Qdrant
        String memoryId = qdrantService.addMemory(content, embedding, numericIds);

        return memoryId;
    }

    /**
     * Добавляет числовой параметр
     * Парсит строку типа "quantity:5:шт" -> название:значение:единица
     */
    private long addNumericParameter(String memoryId, String paramSpec) {
        try {
            String[] parts = paramSpec.split(":");
            if (parts.length < 2) return -1;

            long id = nextNumericId++;
            NumericParameter param = new NumericParameter(
                    id,
                    memoryId,
                    Double.parseDouble(parts[1]),
                    parts[0],
                    parts.length > 2 ? parts[2] : ""
            );

            numericStorage.put(id, param);
            return id;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Поиск похожих воспоминаний
     * ВАЖНО: обновляет lastTriggeredAt для всех найденных воспоминаний!
     */
    public List<Memory> searchMemories(String query) throws Exception {
        List<Float> embedding = embeddingService.generateEmbedding(query);
        List<Memory> results = qdrantService.searchSimilarMemories(embedding, 5);

        // Обновляем время вспоминания для всех найденных воспоминаний
        // Это предотвращает их суммаризацию/забывание, так как они активно используются
        for (Memory memory : results) {
            qdrantService.triggerMemory(memory.getId());
        }

        return results;
    }

    /**
     * Вспомнить воспоминание (увеличить его значимость)
     * ВАЖНО: обновляет lastTriggeredAt, предотвращая забывание!
     */
    public void rememberMemory(String memoryId) throws Exception {
        qdrantService.triggerMemory(memoryId);
        System.out.println("💭 Вспомнено: " + memoryId);
    }

    /**
     * Суммаризирует одно воспоминание
     */
    public void summarizeSingleMemory(Memory memory) throws Exception {
        // Генерируем суммаризацию через AvatarBrain
        String prompt = "Очень кратко суммаризируй это воспоминание в одно предложение, сохраняя только суть:\n" +
                        memory.getContent();
        String summarized = brain.think(prompt);

        // Генерируем новое эмбеддинг для суммаризированного текста
        List<Float> newEmbedding = embeddingService.generateEmbedding(summarized);

        // Обновляем в Qdrant
        qdrantService.summarizeMemory(memory.getId(), summarized, newEmbedding);
    }

    /**
     * Забывает одно воспоминание и удаляет его числовые параметры (CASCADE)
     */
    public void forgetSingleMemory(Memory memory) throws Exception {
        System.out.println("\n❌ Забываю: " + memory.getContent().substring(0, Math.min(50, memory.getContent().length())) + "...");

        // Удаляем связанные числовые параметры (CASCADE)
        if (memory.getRelatedNumericIds() != null) {
            for (Long numId : memory.getRelatedNumericIds()) {
                NumericParameter param = numericStorage.remove(numId);
                if (param != null) {
                    System.out.println("  → Удален параметр: " + param.getParameterName() + " = " + param.getValue());
                }
            }
        }

        // Удаляем воспоминание из Qdrant
        qdrantService.forgetMemory(memory.getId());
        System.out.println("✓ Забыто: " + memory.getId());
    }

    /**
     * Ежедневная процедура суммаризации
     * Выполняется раз в сутки или при перегрузке контекста
     */
    public void runDailySummarization() throws Exception {
        System.out.println("\n⏰ === ЕЖЕДНЕВНАЯ СУММАРИЗАЦИЯ ===");

        // Получаем свежие воспоминания которые нужно суммаризировать
        List<Memory> freshMemories = qdrantService.getFreshMemories();
        int summarizedCount = 0;

        for (Memory memory : freshMemories) {
            if (qdrantService.shouldSummarize(memory)) {
                try {
                    summarizeSingleMemory(memory);
                    summarizedCount++;
                } catch (Exception e) {
                    System.err.println("Ошибка суммаризации: " + e.getMessage());
                }
            }
        }

        System.out.println("✓ Суммаризировано воспоминаний: " + summarizedCount);
    }

    /**
     * Процедура забывания для старых воспоминаний
     * Выполняется при перегрузке контекста или по расписанию
     */
    public void runForgetProcedure() throws Exception {
        System.out.println("\n⏰ === ПРОЦЕДУРА ЗАБЫВАНИЯ ===");

        // Получаем все свежие воспоминания
        List<Memory> freshMemories = qdrantService.getFreshMemories();
        int forgottenCount = 0;

        for (Memory memory : freshMemories) {
            if (qdrantService.shouldForget(memory)) {
                try {
                    forgetSingleMemory(memory);
                    forgottenCount++;
                } catch (Exception e) {
                    System.err.println("Ошибка забывания: " + e.getMessage());
                }
            }
        }

        // Если контекст перегружен - забыть самые старые воспоминания с низкой важностью
        if (qdrantService.isContextOverloaded()) {
            System.out.println("⚠️  Контекст перегружен! Удаляю старые воспоминания...");
            // Дополнительная логика удаления при перегрузке
        }

        System.out.println("✓ Забыто воспоминаний: " + forgottenCount);
    }

    /**
     * Получает все числовые параметры
     */
    public Map<Long, NumericParameter> getAllNumericParameters() {
        return new HashMap<>(numericStorage);
    }

    /**
     * Получает числовой параметр по ID
     */
    public NumericParameter getNumericParameter(long id) {
        return numericStorage.get(id);
    }

    /**
     * Получает параметры для воспоминания
     */
    public List<NumericParameter> getMemoryParameters(String memoryId) {
        return numericStorage.values().stream()
                .filter(p -> p.getMemoryId().equals(memoryId))
                .toList();
    }

    /**
     * Полностью очищает всю базу памяти (100% очистка)
     * Удаляет все воспоминания из Qdrant и очищает локальное хранилище числовых параметров
     */
    public void clearAllMemories() throws Exception {
        System.out.println("\n⚠️  === ПОЛНАЯ ОЧИСТКА ПАМЯТИ ===");
        System.out.println("🗑️  Это действие нельзя отменить!");

        // Очищаем локальное хранилище числовых параметров
        System.out.println("🔄 Очищаю локальное хранилище параметров...");
        int paramCount = numericStorage.size();
        numericStorage.clear();
        numericAccessTime.clear();
        System.out.println("✓ Очищено параметров: " + paramCount);

        // Очищаем Qdrant
        System.out.println("🔄 Очищаю базу Qdrant...");
        qdrantService.clearAllMemories();

        // Сбрасываем счётчик ID
        nextNumericId = 1;

        System.out.println("\n✅ ПОЛНАЯ ОЧИСТКА ЗАВЕРШЕНА!\n");
    }
}

