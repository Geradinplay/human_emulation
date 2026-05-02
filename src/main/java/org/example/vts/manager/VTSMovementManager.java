package org.example.vts.manager;

import org.example.vts.animations.AnimationRegistry;
import org.example.vts.animations.generators.VTSIdleGenerator;
import org.example.vts.client.VTubeStudioClient;
import org.example.vts.expressions.ExpressionRegistry;
import org.example.vts.expressions.interfaces.NotAnimatedExpression;
import org.example.vts.log.VTSLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class VTSMovementManager {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> clearTask;

    private final VTubeStudioClient client;
    private final VTSIdleGenerator idleGenerator;

    private final Map<String, Double> targetWeights = new ConcurrentHashMap<>();
    private final Map<String, Double> currentWeights = new ConcurrentHashMap<>();

    private String lastActiveExpression = null;

    private ExpressionRegistry expressionRegistry;
    private boolean idleEnabled = true;
    private double sharpness = 0.05;

    private Thread workerThread;
    private volatile boolean running = true;

    public VTSMovementManager(VTubeStudioClient client) {
        this.client = client;
        this.idleGenerator = new VTSIdleGenerator(client);
        this.expressionRegistry = new ExpressionRegistry(client);
    }

    public void start() {
        if (workerThread != null && workerThread.isAlive()) return;

        running = true;
        workerThread = new Thread(() -> {
            VTSLogger.info("Movement Manager запущен. 60 FPS");

            try {
                int frameCounter = 0;

                while (running) {
                    frameCounter++;

                    if (client.isOpen() && client.isAuthenticated()) {

                        updateLerp();

                        Map<String, Double> finalWeights = new HashMap<>(currentWeights);

                        if (idleEnabled) {
                            Map<String, Double> offsets = idleGenerator.getIdleOffsets();

                            offsets.forEach((key, value) -> {
                                double base = finalWeights.getOrDefault(key, 0.0);
                                finalWeights.put(key, base + value);
                            });
                        }

                        client.sendWeights(finalWeights);
                    }

                    Thread.sleep(16);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                VTSLogger.info("Movement Manager остановлен.");
            }
        }, "VTS-Worker-Thread");

        workerThread.start();
    }

    // ---------------- EXPRESSIONS ----------------

    public void activateExpression(String expressionName) {
        if (expressionName == null) return;

        String name = expressionName.toLowerCase().trim();
        var expression = expressionRegistry.get(name);

        if (expression == null) return;
        if (name.equals(lastActiveExpression)) return;

        if (lastActiveExpression != null) {
            client.sendExpression(lastActiveExpression, false);
        }

        client.sendExpression("drop", true);
        client.sendExpression(name, true);

        lastActiveExpression = name;

        expression.apply(idleGenerator);
    }

    public void resetAll() {
        if (lastActiveExpression != null) {
            client.sendExpression(lastActiveExpression, false);
        }

        client.sendExpression("drop", true);
        lastActiveExpression = null;

        targetWeights.replaceAll((k, v) -> 0.0);
    }

    public void forceReset(Map<String, Double> resetWeights) {
        targetWeights.putAll(resetWeights);
        currentWeights.putAll(resetWeights);
        client.sendWeights(new HashMap<>(currentWeights));
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
        this.idleEnabled = enabled;
    }

    public void setSharpness(double sharpness) {
        this.sharpness = Math.max(0.001, Math.min(1.0, sharpness));
    }

    // ---------------- HIGH LEVEL ----------------

    public void triggerExpression(String em) {
        if (em == null || em.isEmpty()) return;

        cleanAnimation();

        String name = em.toLowerCase().trim();

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

            if (expression instanceof NotAnimatedExpression timed) {
                clearTask = scheduler.schedule(
                        this::clearExpression,
                        timed.duration(),
                        TimeUnit.MILLISECONDS
                );
            }

            return;
        }

        System.out.println("[DEBUG] Unknown emotion: " + name);
    }

    public void clearExpression() {
        client.sendExpression(lastActiveExpression, false);
        client.sendExpression("drop", true);
        lastActiveExpression = null;
    }

    // ---------------- ANIMATIONS ----------------

    public void triggerAnimation(String anim) {
        if (anim == null) return;

        cleanAnimation();

        var animation = AnimationRegistry.get(anim);

        if (animation != null) {
            idleGenerator.addOverlay(animation, animation.duration());
        } else {
            System.out.println("[DEBUG] Unknown animation: " + anim);
        }
    }

    public void triggerAnimationWithExpression(String anim, String exp) {
        if (anim == null || exp == null) return;

        cleanAnimation();

        var animation = AnimationRegistry.get(anim);
        var expression = expressionRegistry.get(exp);

        if (animation != null && expression != null) {

            expression.apply(idleGenerator);
            idleGenerator.addOverlay(animation, animation.duration());

            if (clearTask != null && !clearTask.isDone()) {
                clearTask.cancel(false);
            }

            clearTask = scheduler.schedule(
                    this::clearExpression,
                    animation.duration(),
                    TimeUnit.MILLISECONDS
            );

        } else {
            if (animation == null) {
                System.out.println("[DEBUG] Unknown animation: " + anim);
            }
            if (expression == null) {
                System.out.println("[DEBUG] Unknown expression: " + exp);
            }
        }
    }

    public void cleanAnimation() {
        idleGenerator.clearOverlays();
    }
}