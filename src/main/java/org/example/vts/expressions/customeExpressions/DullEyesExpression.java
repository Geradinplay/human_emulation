package org.example.vts.expressions.customeExpressions;

import org.example.vts.animations.customeAnimations.BlushBehavior;
import org.example.vts.animations.generators.VTSIdleGenerator;
import org.example.vts.client.VTubeStudioClient;
import org.example.vts.expressions.interfaces.ExpressionBehavior;
import org.example.vts.expressions.interfaces.NotAnimatedExpression;

public class DullEyesExpression implements ExpressionBehavior, NotAnimatedExpression {
    private long duration=5000;


    @Override
    public String getName() {
        return "dull_eyes";
    }

    @Override
    public void apply(VTSIdleGenerator idleGenerator) {

    }

    @Override
    public long duration() {
        return duration;
    }
}
