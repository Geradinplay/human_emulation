package org.example.vts.animations.customeAnimations;

import org.example.vts.animations.generators.VTSIdleGenerator;
import org.example.vts.animations.interfaces.AnimationBehavior;


import java.util.HashMap;
import java.util.Map;

public class BlushBehavior implements AnimationBehavior{

    long duration = 30000;

    public long duration() {
        return duration;
    }
    private enum State {
        LOOKING_AWAY,
        SLOW_LOOK,
        SNAP_BACK
    }

    private State state = State.LOOKING_AWAY;
    private long nextActionTime = 0;
    private int side = Math.random() > 0.5 ? 1 : -1;

    private double angleX = 0;
    private double angleY = 0;
    private double bodyY = 0;
    private double eyeX = 0;

    private double panicThreshold = 0.3;

    private double smooth(double current, double target, double speed) {
        return current + (target - current) * speed;
    }

    @Override
    public Map<String, Double> update(double time) {
        long now = System.currentTimeMillis();

        if (now > nextActionTime) {
            switch (state) {
                case LOOKING_AWAY -> {
                    state = State.SLOW_LOOK;
                    panicThreshold = 0.2 + (Math.random() * 0.5);
                    nextActionTime = now + 4000;
                }
                case SLOW_LOOK -> {
                    state = State.LOOKING_AWAY;
                    setNextPause(now, false);
                }
                case SNAP_BACK -> {
                    state = State.LOOKING_AWAY;
                    setNextPause(now, true);
                }
            }
        }

        switch (state) {
            case LOOKING_AWAY -> {
                double targetX = side * 35;
                angleX = smooth(angleX, targetX, 0.07);
                angleY = smooth(angleY, -10, 0.07);
                // Спокойное состояние — тело чуть припущено
                bodyY = smooth(bodyY, -5, 0.07);
                eyeX = smooth(eyeX, -side * 0.8, 0.07);
            }

            case SLOW_LOOK -> {
                angleX = smooth(angleX, 0, 0.02);
                angleY = smooth(angleY, -5, 0.02);
                eyeX = smooth(eyeX, 0, 0.04);
                // Когда начинает смотреть, тело чуть приподнимается (напряжение)
                bodyY = smooth(bodyY, 0, 0.02);

                double progress = 1.0 - (Math.abs(angleX) / 35.0);

                if (progress > panicThreshold) {
                    state = State.SNAP_BACK;
                    nextActionTime = now + 800;
                }
            }

            case SNAP_BACK -> {
                angleX = smooth(angleX, side * 42, 0.45);
                angleY = smooth(angleY, -14, 0.45);
                eyeX = smooth(eyeX, side * 1.3, 0.5);
                // МОМЕНТ СМУЩЕНИЯ: резко "вжимается" вниз
                // Используем значение побольше (в пределах твоих -35)
                bodyY = smooth(bodyY, -25, 0.4);
            }
        }

        double micro = Math.sin(time * 5) * 1.3;

        // Создаем карту параметров (через HashMap, так как Map.of имеет лимит аргументов)
        Map<String, Double> results = new HashMap<>();
        results.put("ParamAngleX", angleX + micro);
        results.put("ParamAngleY", angleY);
        results.put("ParamAngleZ", angleX * 0.2);
        results.put("ParamBodyX", angleX * 0.1);
        results.put("ParamBodyY", bodyY); // Теперь bodyY меняется во всех состояниях
        results.put("ParamEyeBallX", eyeX);
        results.put("ParamEyeBallY", -0.1);
        results.put("ParamBreath", (state == State.SNAP_BACK) ? 1.4 : 0.7);

        return results;
    }

    /**
     * Логика пауз: иногда короткие (серия взглядов), иногда длинные.
     */
    private void setNextPause(long now, boolean afterPanic) {
        double chance = Math.random();

        // Если это серия быстрых взглядов (60% шанс при испуге)
        if (afterPanic && chance < 0.6) {
            // Короткая пауза перед следующей попыткой посмотреть (1.5 - 3 сек)
            nextActionTime = now + 1500 + (long)(Math.random() * 1500);
        } else {
            // Обычная пауза (3 - 7 секунд)
            nextActionTime = now + 3000 + (long)(Math.random() * 4000);
        }
    }

    @Override
    public void play(VTSIdleGenerator idleGenerator) {
        idleGenerator.setTemporaryBehavior(this, 30000L );
    }

    @Override
    public String getName() {
        return "shyblush";
    }
}