package org.example.vts.expressions.customeExpressions;

import org.example.vts.animations.generators.VTSIdleGenerator;
import org.example.vts.expressions.interfaces.ExpressionBehavior;
import org.example.vts.expressions.interfaces.NotAnimatedExpression;

public class SuspiciousExpression implements ExpressionBehavior , NotAnimatedExpression {
    private long duration=5000;
    @Override
    public String getName() {
        return "suspicious";
    }

    @Override
    public void apply(VTSIdleGenerator idleGenerator) {

    }

    @Override
    public long duration() {
        return duration;
    }
}
