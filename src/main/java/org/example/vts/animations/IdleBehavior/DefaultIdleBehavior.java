package org.example.vts.animations.IdleBehavior;

import org.example.vts.animations.generators.VTSIdleGenerator;
import org.example.vts.animations.interfaces.AnimationBehavior;

import java.util.Map;
import java.util.Random;

public class DefaultIdleBehavior implements AnimationBehavior {

    private final Random random = new Random();

    private double lastX = 0, lastY = 0, lastZ = 0, lastBody = 0;
    private double lastEyeX = 0, lastEyeY = 0;

    private double impulseX = 0;
    private double impulseY = 0;
    private double impulseTimer = 0;

    private static final double EYE_X_LIMIT = 0.45;
    private static final double EYE_Y_UP_LIMIT = 0.1;
    private static final double EYE_Y_DOWN_LIMIT = -0.35;

    @Override
    public Map<String, Double> update(double time) {

        if (time > impulseTimer) {
            impulseX = (random.nextDouble() * 80) - 40;
            impulseY = (random.nextDouble() > 0.8) ? (random.nextDouble() * -6.0) : 0;
            impulseTimer = time + 2.5 + random.nextDouble() * 4.0;
        }

        // --- ГОЛОВА ---
        double targetX = (Math.sin(time * 0.15) * 4.0) + impulseX;
        double targetY = (Math.sin(time * 0.1) * 0.5) + impulseY;
        double targetZ = Math.sin(time * 0.15) * 5.0;

        // ПЛАВНОСТЬ
        lastX += (targetX - lastX) * 0.12;
        lastY += (targetY - lastY) * 0.05;
        lastZ += (targetZ - lastZ) * 0.08;

        // --- ТЕЛО ---
        double targetBody = lastX * 0.35;
        lastBody += (targetBody - lastBody) * 0.04;

        // --- ГЛАЗА ---
        double rawEyeX = (lastX * 0.01) + (Math.sin(time * 1.1) * 0.35);
        double rawEyeY = (lastY * 0.02) + (Math.cos(time * 0.9) * 0.1);

        double clampedEyeX = Math.max(-EYE_X_LIMIT, Math.min(EYE_X_LIMIT, rawEyeX));
        double clampedEyeY = Math.max(EYE_Y_DOWN_LIMIT, Math.min(EYE_Y_UP_LIMIT, rawEyeY));

        lastEyeX += (clampedEyeX - lastEyeX) * 0.6;
        lastEyeY += (clampedEyeY - lastEyeY) * 0.6;

        return Map.of(
                "ParamAngleX", lastX,
                "ParamAngleY", lastY,
                "ParamAngleZ", lastZ,
                "ParamBodyAngleX", lastBody,
                "ParamEyeBallX", lastEyeX,
                "ParamEyeBallY", lastEyeY
        );
    }

    @Override
    public long duration() {
        return 0;
    }

    @Override
    public void play(VTSIdleGenerator idleGenerator) {
        idleGenerator.setTemporaryBehavior(this, 2000L);
    }
    @Override
    public String getName() {
        return "default_idle";
    }
}