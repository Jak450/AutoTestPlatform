package org.example.ai_study_notes.agent.middleware;

import org.example.ai_study_notes.agent.middleware.Middleware;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 清洗用户输入中的注入标签（保留原文溯源，仅中和框架标签）。
 */
@Component
public class InputSanitizationMiddleware implements Middleware {

    @Override
    public int order() {
        return 1;
    }

    @Override
    public Optional<org.example.ai_study_notes.agent.tool.ToolResult> before(
            String toolName, Map<String, Object> args, ToolContext context) {
        if (args == null) {
            return Optional.empty();
        }
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            if (entry.getValue() instanceof String text) {
                entry.setValue(sanitize(text));
            }
        }
        return Optional.empty();
    }

    private String sanitize(String text) {
        return text
                .replaceAll("(?i)<system[ >]", "&lt;system ")
                .replaceAll("(?i)<assistant[ >]", "&lt;assistant ")
                .replaceAll("(?i)<user[ >]", "&lt;user ")
                .replaceAll("(?i)<tool[ >]", "&lt;tool ");
    }
}
