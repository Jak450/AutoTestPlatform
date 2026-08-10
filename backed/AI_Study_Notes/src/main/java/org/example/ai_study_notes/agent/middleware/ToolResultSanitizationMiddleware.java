package org.example.ai_study_notes.agent.middleware;

import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 工具结果中和：对不可信来源的结果中和框架标签，防止注入。
 */
@Component
public class ToolResultSanitizationMiddleware implements Middleware {

    @Override
    public int order() {
        return 3;
    }

    @Override
    public void after(String toolName, Map<String, Object> args, ToolResult result, ToolContext context) {
        if (result == null) {
            return;
        }
        if (result.getMessage() != null) {
            result.setMessage(sanitize(result.getMessage()));
        }
        if (result.getData() instanceof String text) {
            result.setData(sanitize(text));
        } else if (result.getData() instanceof Map<?, ?> rawMap) {
            Object content = rawMap.get("content");
            if (content instanceof String text) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) rawMap;
                map.put("content", sanitize(text));
            }
        }
    }

    private String sanitize(String text) {
        return text
                .replaceAll("(?i)<system[ >]", "&lt;system ")
                .replaceAll("(?i)<assistant[ >]", "&lt;assistant ")
                .replaceAll("(?i)<user[ >]", "&lt;user ")
                .replaceAll("(?i)<tool[ >]", "&lt;tool ");
    }
}
