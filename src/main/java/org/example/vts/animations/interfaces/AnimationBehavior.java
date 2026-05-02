package org.example.vts.animations.interfaces;

import org.example.vts.animations.generators.VTSIdleGenerator;

import java.util.Map;

public interface AnimationBehavior {
    public String getName();

    void play(VTSIdleGenerator idleGenerator);

    Map<String, Double> update(double time);

    long duration();


}
