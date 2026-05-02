package org.example.vts.expressions;

import org.example.vts.client.VTubeStudioClient;
import org.example.vts.expressions.customeExpressions.*;
import org.example.vts.expressions.interfaces.ExpressionBehavior;

import java.util.HashMap;
import java.util.Map;

public class ExpressionRegistry {

    private static final Map<String, ExpressionBehavior> expressions = new HashMap<>();

    // Конструктор теперь принимает клиента и сам регистрирует все нужные выражения
    public ExpressionRegistry(VTubeStudioClient client) {
        register(new HappyExpression());
        register(new AngryExpression());
        register(new DropExpression());
        register(new ShockExpression());
        register(new BlushExpression(client));
        register(new SuspiciousExpression());
        register(new DullEyesExpression());
    }

    public void register(ExpressionBehavior exp) {
        expressions.put(exp.getName().toLowerCase(), exp);
    }

    public ExpressionBehavior get(String name) {
        return expressions.get(name.toLowerCase());
    }

    public Map<String, ExpressionBehavior> getAll() {
        return expressions;
    }
}