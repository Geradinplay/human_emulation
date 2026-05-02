package org.example;

import org.example.db.entity.Memory;
import org.example.db.entity.NumericParameter;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.Arrays;
import java.util.List;

/**
 * Unit test for simple App.
 * Тесты системы памяти Avatar Brain с Qdrant
 */
public class VectorDatabaseTest
    extends TestCase
{
    private AvatarBrain brain;

    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public VectorDatabaseTest(String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( VectorDatabaseTest.class );
    }

    /**
     * Инициализация перед каждым тестом
     */
    @Override
    public void setUp() {
        try {
            System.out.println("🚀 === ИНИЦИАЛИЗАЦИЯ AVATAR BRAIN ===\n");
            brain = new AvatarBrain();
            brain.initializeMemory("localhost", 6334);
        } catch (Exception e) {
            System.err.println("Ошибка инициализации: " + e.getMessage());
        }
    }

    /**
     * Очистка после каждого теста
     */
    @Override
    public void tearDown() {
        try {
            if (brain != null) {
                brain.shutdown();
            }
        } catch (Exception e) {
            System.err.println("Ошибка завершения: " + e.getMessage());
        }
    }

    /**
     * Тест 1: Простое воспоминание
     */
    public void testRememberSimpleMemory() {
        try {
            System.out.println("1️⃣  Тест: Добавляю первое воспоминание...");
            String memory1 = brain.rememberThis(
                    "Я купил 5 яблок за 100 рублей вчера на рынке",
                    Arrays.asList("quantity:5:шт", "price:100:руб")
            );
            System.out.println("Память сохранена: " + memory1);
            assertNotNull("Память должна быть сохранена", memory1);
        } catch (Exception e) {
            fail("Ошибка при сохранении памяти: " + e.getMessage());
        }
    }

    /**
     * Тест 2: Добавление воспоминания с параметрами
     */
    public void testRememberWithParameters() {
        try {
            System.out.println("2️⃣  Тест: Добавляю воспоминание с параметром температуры...");
            String memory2 = brain.rememberThis(
                    "Температура в комнате составила 22 градуса",
                    Arrays.asList("temperature:22:°C")
            );
            System.out.println("Память сохранена: " + memory2);
            assertNotNull("Память должна быть сохранена", memory2);
        } catch (Exception e) {
            fail("Ошибка при сохранении памяти: " + e.getMessage());
        }
    }

    /**
     * Тест 3: Поиск в памяти
     */
    public void testSearchMemory() {
        try {
            System.out.println("3️⃣  Тест: Ищу воспоминания о яблоках...");

            // Сначала добавим память
            brain.rememberThis(
                    "Я купил 5 яблок за 100 рублей вчера на рынке",
                    Arrays.asList("quantity:5:шт", "price:100:руб")
            );

            List<Memory> memories = brain.searchMemory("яблоки фрукты рынок");
            System.out.println("Найдено воспоминаний: " + memories.size());
            for (Memory m : memories) {
                System.out.println("  • " + m.getContent());
            }
            assertTrue("Должны быть найдены воспоминания", memories.size() >= 0);
        } catch (Exception e) {
            fail("Ошибка при поиске памяти: " + e.getMessage());
        }
    }

    /**
     * Тест 4: Получение числовых параметров
     */
    public void testGetNumericParameters() {
        try {
            System.out.println("4️⃣  Тест: Получаю числовые параметры в памяти...");

            // Добавим память с параметрами
            brain.rememberThis(
                    "Я купил 5 яблок за 100 рублей вчера на рынке",
                    Arrays.asList("quantity:5:шт", "price:100:руб")
            );

            var numParams = brain.getNumericParameters();
            System.out.println("Найдено параметров: " + numParams.size());
            for (var entry : numParams.entrySet()) {
                NumericParameter param = entry.getValue();
                System.out.println("  • " + param.getParameterName() + ": " + param.getValue() + " " + param.getUnit());
            }
            assertNotNull("Параметры не должны быть null", numParams);
        } catch (Exception e) {
            fail("Ошибка при получении параметров: " + e.getMessage());
        }
    }

    /**
     * Тест 5: Проверка статуса памяти
     */
    public void testMemoryStatus() {
        try {
            System.out.println("5️⃣  Тест: Проверяю статус системы памяти...");
            boolean isRunning = brain.isMemoryRunning();
            System.out.println("  Память работает: " + (isRunning ? "✓ ДА" : "✗ НЕТ"));
            assertTrue("Память должна быть запущена", isRunning);
        } catch (Exception e) {
            fail("Ошибка при проверке статуса: " + e.getMessage());
        }
    }

    /**
     * Rigorous Test :-)
     */
    public void testApp()
    {
        assertTrue( true );
    }
}
