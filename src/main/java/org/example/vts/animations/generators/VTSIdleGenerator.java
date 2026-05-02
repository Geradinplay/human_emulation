package org.example.vts.animations.generators;

import org.example.vts.animations.IdleBehavior.DefaultIdleBehavior;
import org.example.vts.animations.interfaces.AnimationBehavior;
import org.example.vts.client.VTubeStudioClient;

import java.util.*;

public class VTSIdleGenerator {

    private final VTubeStudioClient client;
    private double time = 0;

    private final AnimationBehavior defaultBehavior = new DefaultIdleBehavior();
    private AnimationBehavior baseBehavior = defaultBehavior;

    private final List<OverlayEntry> overlays = new ArrayList<>();

    public VTSIdleGenerator(VTubeStudioClient client) {
        this.client = client;
    }

    public VTubeStudioClient getVTubeStudioClient() {
        return client;
    }

    public Map<String, Double> getIdleOffsets() {
        time += 0.025;
        long now = System.currentTimeMillis();

        // базовая анимация
        Map<String, Double> result = new HashMap<>(baseBehavior.update(time));

        // обработка overlay
        Iterator<OverlayEntry> iterator = overlays.iterator();
        while (iterator.hasNext()) {
            OverlayEntry entry = iterator.next();

            if (now > entry.endTime) {
                // callback при окончании
                if (entry.onEnd != null) {
                    entry.onEnd.run();
                }
                iterator.remove();
                continue;
            }

            Map<String, Double> overlayValues = entry.behavior.update(time);

            // смешивание (additive с весом)
            for (Map.Entry<String, Double> e : overlayValues.entrySet()) {
                result.merge(e.getKey(), e.getValue() * entry.weight, Double::sum);
            }
        }

        return result;
    }

    // ---------------- BASE ----------------

    public void setBaseBehavior(AnimationBehavior behavior) {
        this.baseBehavior = behavior != null ? behavior : defaultBehavior;
    }

    public void resetBaseBehavior() {
        this.baseBehavior = defaultBehavior;
    }

    // ---------------- OVERLAY ----------------

    public void addOverlay(AnimationBehavior behavior, long durationMs) {
        addOverlay(behavior, durationMs, 1.0, null);
    }

    public void addOverlay(AnimationBehavior behavior, long durationMs, double weight) {
        addOverlay(behavior, durationMs, weight, null);
    }

    public void addOverlay(AnimationBehavior behavior, long durationMs, double weight, Runnable onEnd) {
        overlays.add(new OverlayEntry(
                behavior,
                System.currentTimeMillis() + durationMs,
                weight,
                onEnd
        ));
    }

    public void clearOverlays() {
        overlays.clear();
    }

    // ---------------- INTERNAL ----------------

    private static class OverlayEntry {
        AnimationBehavior behavior;
        long endTime;
        double weight;
        Runnable onEnd;

        OverlayEntry(AnimationBehavior behavior, long endTime, double weight, Runnable onEnd) {
            this.behavior = behavior;
            this.endTime = endTime;
            this.weight = weight;
            this.onEnd = onEnd;
        }
    }
}