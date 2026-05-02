package org.example.vts.client;

import org.example.vts.VTSLauncher;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExpressionHandlerStub {





    // Регулярка под твой новый промт
    private static final Pattern FORMAT_PATTERN =
            Pattern.compile("\\[animation:\\s*([^,]+),\\s*expression:\\s*([^\\]]+)\\]");

    public String extractExpression(String text) {
        if (text == null) return null;
        Matcher matcher = FORMAT_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(2).replace("expression:", "").trim();
        }
        return null;
    }

    public String extractAnimation(String text) {
        if (text == null) return null;
        Matcher matcher = FORMAT_PATTERN.matcher(text);
        if (matcher.find()) {
            String anim = matcher.group(1).replace("animation:", "").trim();
            return anim.equalsIgnoreCase("default") ? null : anim;
        }
        return null;
    }
}