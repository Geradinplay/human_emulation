package org.example.vts.animations;

import org.example.vts.animations.IdleBehavior.DefaultIdleBehavior;
import org.example.vts.animations.customeAnimations.HeadShakeBehavior;
import org.example.vts.animations.interfaces.AnimationBehavior;
import org.example.vts.expressions.customeExpressions.BlushExpression;
import org.example.vts.expressions.customeExpressions.ShockExpression;

import java.util.HashMap;
import java.util.Map;

public class AnimationRegistry {

    private static final Map<String, AnimationBehavior> animations = new HashMap<>();

    static {
        register(new DefaultIdleBehavior());
        register(new HeadShakeBehavior());
        //  добавляешь тут — UI НЕ трогаешь
    }

    public static void register(AnimationBehavior behavior) {
        animations.put(behavior.getName(), behavior);
    }

    public static Map<String, AnimationBehavior> getAll() {
        return animations;
    }
    public static AnimationBehavior get(String name) {
        if (name == null) return null;
        return animations.get(name);
    }
}