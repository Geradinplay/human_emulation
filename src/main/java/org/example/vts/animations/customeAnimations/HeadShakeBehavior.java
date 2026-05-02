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

        // Скорость чуть выше для динамики
        double speed = 5.0;
        phase += speed * deltaTime;

        double rawSin = Math.sin(phase);

        // --- МЕТОД "МЯГКОГО УПОРА" ---
        // Используем комбинацию синуса и его усиления.
        // Это даст широкий размах в центре и резкое, но плавное торможение у краев.
        double sinValue = (1.5 * rawSin) - (0.5 * Math.pow(rawSin, 3));

        // Интенсивность ставим чуть больше лимита VTS,
        // чтобы точно "дожать" до 30.
        double intensityX = 32.0;
        double intensityZ = 18.0;

        double currentX = sinValue * intensityX;
        double currentZ = -sinValue * intensityZ;

        // Y для "живости"
        double currentY = -8 + Math.cos(phase * 1.5) * 2;

        // Ограничиваем вручную, чтобы не было микро-прыжков за лимит
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
        // Сбрасываем фазу при каждом запуске, чтобы начинать из центра
        phase = 0;
        idleGenerator.setTemporaryBehavior(this, 4000L);
    }

    @Override
    public String getName() {
        return "HeadShakeSmooth";
    }
}