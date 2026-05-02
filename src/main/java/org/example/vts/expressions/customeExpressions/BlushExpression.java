package org.example.vts.expressions.customeExpressions;

import org.example.vts.animations.customeAnimations.BlushBehavior;
import org.example.vts.animations.generators.VTSIdleGenerator;
import org.example.vts.client.VTubeStudioClient;
import org.example.vts.expressions.interfaces.ExpressionBehavior;

public class BlushExpression implements ExpressionBehavior {

    private final VTubeStudioClient client;

    public BlushExpression(VTubeStudioClient client) {
        this.client = client;
    }

    @Override
    public String getName() {
        return "blush";
    }

    @Override
    public void apply(VTSIdleGenerator idleGenerator) {

        BlushBehavior behavior = new BlushBehavior();

        // ✅ новый API
        idleGenerator.addOverlay(
                behavior,
                20000L,
                1.0,
                () -> client.sendExpression("blush", false)
        );
    }
}