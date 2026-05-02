package org.example.vts.animations.customeAnimations;

import org.example.vts.animations.generators.VTSIdleGenerator;
import org.example.vts.animations.interfaces.AnimationBehavior;

import java.util.HashMap;
import java.util.Map;

public class HeadShakeBehavior implements AnimationBehavior {

    long duration = 3000;

    public long duration() {
        return duration;
    }

    private double phase = 0;
    private double lastTime = -1;

    private final Map<String, Double> map = new HashMap<>();

    @Override
    public Map<String, Double> update(double time) {
        if (lastTime < 0) lastTime = time;
        double deltaTime = time - lastTime;
        lastTime = time;

        if (deltaTime > 0.05) deltaTime = 0.05;

        double speed = 5.0;
        phase += speed * deltaTime;

        double rawSin = Math.sin(phase);

        double sinValue = (1.5 * rawSin) - (0.5 * Math.pow(rawSin, 3));

        double intensityX = 32.0;
        double intensityZ = 18.0;

        double currentX = sinValue * intensityX;
        double currentZ = -sinValue * intensityZ;

        double currentY = -8 + Math.cos(phase * 1.5) * 2;

        currentX = Math.max(-30, Math.min(30, currentX));

        map.put("ParamAngleX", currentX);
        map.put("ParamAngleY", currentY);
        map.put("ParamAngleZ", currentZ);

        map.put("ParamBodyAngleX", currentX * 0.4);
        map.put("ParamBodyAngleZ", currentZ * 0.3);

        return map;
    }

    @Override
    public void play(VTSIdleGenerator idleGenerator) {
        phase = 0;
        lastTime = -1;

        // ✅ НОВЫЙ API
        idleGenerator.addOverlay(this, duration);
    }

    @Override
    public String getName() {
        return "HeadShakeSmooth";
    }
}