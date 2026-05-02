package org.example.vts.animations.generators;

import org.example.vts.animations.IdleBehavior.DefaultIdleBehavior;
import org.example.vts.animations.interfaces.AnimationBehavior;
import org.example.vts.client.VTubeStudioClient;


import java.util.Map;

public class VTSIdleGenerator {
    private final VTubeStudioClient client;
    private double time = 0;

    private Runnable onBehaviorEnd;
    private AnimationBehavior defaultBehavior = new DefaultIdleBehavior();
    private AnimationBehavior behavior = defaultBehavior;

    private long behaviorEndTime = 0;

    public VTSIdleGenerator(VTubeStudioClient client) {
        this.client = client;
    }

    public VTubeStudioClient getVTubeStudioClient() {
        return client;
    }

    public Map<String, Double> getIdleOffsets() {
        time += 0.025;

        long now = System.currentTimeMillis();

        // возврат к дефолту + событие окончания
        if (now > behaviorEndTime) {
            if (behavior != defaultBehavior) {
                behavior = defaultBehavior;

                //  callback вызывается РОВНО в момент окончания
                if (onBehaviorEnd != null) {
                    onBehaviorEnd.run();
                    onBehaviorEnd = null; // чтобы не повторялся
                }
            }
        }

        return behavior.update(time);
    }
    public void forceReturnToDefault() {
        if (behavior != defaultBehavior) {
            behavior = defaultBehavior;
            behaviorEndTime = 0;

            if (onBehaviorEnd != null) {
                onBehaviorEnd.run();
                onBehaviorEnd = null;
            }
        }
    }

    public void setBehavior(AnimationBehavior behavior) {
        this.behavior = behavior;
    }

    public void setTemporaryBehavior(AnimationBehavior behavior, long durationMs, Runnable onEnd) {
        this.behavior = behavior;
        this.behaviorEndTime = System.currentTimeMillis() + durationMs;
        this.onBehaviorEnd = onEnd;
    }
    public void setTemporaryBehavior(AnimationBehavior behavior, long durationMs) {
        this.behavior = behavior;
        this.behaviorEndTime = System.currentTimeMillis() + durationMs;
    }
}