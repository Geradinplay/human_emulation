package org.example.vts.manager;



import org.example.vts.animations.AnimationRegistry;
import org.example.vts.animations.IdleBehavior.DefaultIdleBehavior;
import org.example.vts.animations.generators.VTSIdleGenerator;
import org.example.vts.client.VTubeStudioClient;
import org.example.vts.expressions.ExpressionRegistry;
import org.example.vts.expressions.interfaces.NotAnimatedExpression;
import org.example.vts.log.VTSLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Единый менеджер движения и состояний.
 * Сочетает в себе:
 * 1. Плавный переход к целевым весам (LERP)
 * 2. Генерацию фонового шума (Idle)
 * 3. Управление файлами выражений (.exp3.json)
 */
public class VTSMovementManager {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> clearTask;
    private final VTubeStudioClient client;
    private final VTSIdleGenerator idleGenerator;

    // Состояния весов
    private final Map<String, Double> targetWeights = new ConcurrentHashMap<>();
    private final Map<String, Double> currentWeights = new ConcurrentHashMap<>();

    // Состояние выражений (пресетов)
    private String lastActiveExpression = null;

    private ExpressionRegistry expressionRegistry;
    private boolean idleEnabled = true;
    private double sharpness = 0.05; // Плавность "доезда" до цели
    private Thread workerThread;
    private volatile boolean running = true;

    public VTSMovementManager(VTubeStudioClient client) {
        this.client = client;
        this.idleGenerator=new VTSIdleGenerator(client);
        this.expressionRegistry = new ExpressionRegistry(client);
    }

    /**
     * Запускает поток обновления (60 FPS)
     */
    public void start() {
        if (workerThread != null && workerThread.isAlive()) return;

        running = true;
        workerThread = new Thread(() -> {
            VTSLogger.info("Movement Manager запущен. Синхронизация: 60 FPS");
            System.out.println("[DEBUG] Movement Manager started - idleEnabled: " + idleEnabled);
            try {
                int frameCounter = 0;
                while (running) {
                    frameCounter++;
                    if (client.isOpen() && client.isAuthenticated()) {
                        // 1. Рассчитываем плавное движение к целям (LERP)
                        updateLerp();

                        // 2. Создаем итоговую карту для отправки
                        Map<String, Double> finalWeights = new HashMap<>(currentWeights);

                        // 3. Накладываем шум поверх текущих плавных значений
                        if (idleEnabled) {
                            Map<String, Double> offsets = idleGenerator.getIdleOffsets();
                            if (frameCounter % 180 == 0) {
                                System.out.println("[DEBUG] IdleGenerator active - offsets size: " + offsets.size());
                            }
                            offsets.forEach((key, value) -> {
                                double currentBase = finalWeights.getOrDefault(key, 0.0);
                                finalWeights.put(key, currentBase + value);
                            });
                        } else {
                            if (frameCounter % 180 == 0) {
                                System.out.println("[DEBUG] WARNING: IdleEnabled is FALSE!");
                            }
                        }

                        // 4. Отправляем финальный "микс" в клиент
                        if (frameCounter % 180 == 0) {
                            System.out.println("[DEBUG] Sending weights - finalWeights: " + finalWeights.size() + ", targetWeights: " + targetWeights.size() + ", currentWeights: " + currentWeights.size());
                        }
                        client.sendWeights(finalWeights);
                    } else {
                        if (frameCounter % 180 == 0) {
                            System.out.println("[DEBUG] Connection broken - isOpen: " + client.isOpen() + ", isAuth: " + client.isAuthenticated());
                        }
                    }

                    // Интервал ~16мс обеспечивает ~60 обновлений в секунду
                    Thread.sleep(16);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                VTSLogger.info("Movement Manager остановлен потоком.");
            }
        }, "VTS-Worker-Thread");

        workerThread.start();
    }

    /**
     * Управление выражениями (.exp3.json)
     */
    public void activateExpression(String expressionName) {
        if (expressionName == null) return;

        String name = expressionName.toLowerCase().trim();

        var expression = expressionRegistry.get(name);

        if (expression == null) {
            System.out.println("[DEBUG] Expression NOT found: " + name);
            return;
        }

        if (name.equals(lastActiveExpression)) return;

        // 1. выключаем старое
        if (lastActiveExpression != null) {
            client.sendExpression(lastActiveExpression, false);
        }

        // 2. СБРОС через drop
        client.sendExpression("drop", true);

        // 3. включаем новое
        client.sendExpression(name, true);
        lastActiveExpression = name;

        // 4. поведение (анимации)
        expression.apply(idleGenerator);
    }

    /**
     * Мягкий сброс всего: возвращает голову в 0 и выключает эмоцию
     */
    public void resetAll() {
        System.out.println("[DEBUG] resetAll() called - targetWeights size: " + targetWeights.size() + ", lastActiveExpression: " + lastActiveExpression);

        // 1. Выключаем текущую эмоцию + делаем drop
        if (lastActiveExpression != null) {
            client.sendExpression(lastActiveExpression, false);
        }

        client.sendExpression("drop", true); // всегда делаем сброс
        lastActiveExpression = null;

        // 2. Сбрасываем веса (плавно через LERP)
        targetWeights.replaceAll((k, v) -> 0.0);

        System.out.println("[DEBUG] resetAll() completed");
    }

    /**
     * Принудительная установка без плавности (телепортация)
     */
    public void forceReset(Map<String, Double> resetWeights) {
        System.out.println("[DEBUG] forceReset() called with " + resetWeights.size() + " params");
        targetWeights.putAll(resetWeights);
        currentWeights.putAll(resetWeights);
        client.sendWeights(new HashMap<>(currentWeights));
        System.out.println("[DEBUG] forceReset() completed");
    }

    private void updateLerp() {
        for (Map.Entry<String, Double> entry : targetWeights.entrySet()) {
            String key = entry.getKey();
            double target = entry.getValue();
            double current = currentWeights.getOrDefault(key, 0.0);

            if (Math.abs(target - current) > 0.0001) {
                double newValue = current + (target - current) * sharpness;
                currentWeights.put(key, newValue);
            } else {
                currentWeights.put(key, target);
            }
        }
    }

    public void stop() {
        running = false;
        if (workerThread != null) workerThread.interrupt();
    }

    public void setIdleEnabled(boolean enabled) {
        System.out.println("[DEBUG] setIdleEnabled() - changing from " + this.idleEnabled + " to " + enabled);
        this.idleEnabled = enabled;
    }

    public void setSharpness(double sharpness) {
        this.sharpness = Math.max(0.001, Math.min(1.0, sharpness));
    }

    /**
     * Метод для ручной или автоматической активации пресета весов.
     * Сопоставляет текстовое название с физическими параметрами Live2D.
     */
    public void triggerExpression(String em) {
        if (em == null || em.isEmpty()) return;
        cleanAnimation();
        String name = em.toLowerCase().trim();

        // отдельная логика для сброса
        if (name.equals("drop")) {
            resetAll();

            return;
        }

        var expression = expressionRegistry.get(name);

        if (expression != null) {
            activateExpression(name);

            if (clearTask != null && !clearTask.isDone()) {
                clearTask.cancel(false);
            }

            if(expression instanceof NotAnimatedExpression timed){
                clearTask = scheduler.schedule(() -> {
                    clearExpression();
                }, timed.duration(), TimeUnit.MILLISECONDS);
            }

            return;
        }

        System.out.println("[DEBUG] Unknown emotion: " + name);
    }

    public void clearExpression() {
        System.out.println("[DEBUG] clearExpression() called");
        client.sendExpression(lastActiveExpression, false);
        client.sendExpression("drop", true);
        lastActiveExpression = null;

    }

    public void triggerAnimation(String anim) {
        if (anim == null) return;
        cleanAnimation();

        System.out.println("[DEBUG] triggerAnimation: " + anim);


        var animation = AnimationRegistry.get(anim);

        if (animation != null) {
            idleGenerator.setTemporaryBehavior(animation, animation.duration());

        } else {
            System.out.println("[DEBUG] Unknown animation: " + anim);
        }
    }

    public void triggerAnimationWithExpression(String anim, String exp) {
        if (anim == null || exp == null) return;
        cleanAnimation();

        System.out.println("[DEBUG] Animation: "+anim+" Expression: "+exp);
        var animation = AnimationRegistry.get(anim);
        var expression = expressionRegistry.get(exp);

        if (animation != null && expression != null) {
            expression.apply(idleGenerator);
            idleGenerator.setTemporaryBehavior(animation, animation.duration());

            if (clearTask != null && !clearTask.isDone()) {
                clearTask.cancel(false);
            }

            clearTask = scheduler.schedule(() -> {
                clearExpression();
            }, animation.duration(), TimeUnit.MILLISECONDS);

        } else {
            if (animation == null) {
                System.out.println("[DEBUG] Unknown animation: " + anim);
            }
            if (expression == null) {
                System.out.println("[DEBUG] Unknown expression: " + expression);
            }
        }
    }

    public void cleanAnimation(){
        System.out.println("[DEBUG] cleanAnimation() called");
        idleGenerator.forceReturnToDefault();
    }


}
