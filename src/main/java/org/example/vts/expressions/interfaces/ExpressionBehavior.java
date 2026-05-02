package org.example.vts.expressions.interfaces;

import org.example.vts.animations.generators.VTSIdleGenerator;

public interface ExpressionBehavior {
    String getName();
    void apply(VTSIdleGenerator idleGenerator);

}