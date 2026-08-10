package org.example.ai_study_notes.agent.middleware;

import org.example.ai_study_notes.agent.middleware.Middleware;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 工具结果字节/字符上限（单条 30KB，超出截断并注明）。
 */
@Component
public class ToolOutputBudgetMiddleware implements Middleware {

    private static final int MAX_RESULT_CHARS = 30_000;

    @Override
    public int order() {
        return 2;
    }

    @Override
    public void after(String toolName, Map<String, Object> args, ToolResult result, ToolContext context) {
        if (result == null || result.getData() == null) {
            return;
        }
        if (result.getData() instanceof String text) {
            if (text.length() > MAX_RESULT_CHARS) {
                result.setData(text.substring(0, MAX_RESULT_CHARS) + "\n...(已截断)");
            }
        } else if (result.getData() instanceof Map<?, ?> rawMap) {
            Object content = rawMap.get("content");
            if (content instanceof String text && text.length() > MAX_RESULT_CHARS) {
                // 工具返回的 Map 可能是不可变 Map（如 Map.of），必须拷贝后再修改
                Map<String, Object> map = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    map.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                map.put("content", text.substring(0, MAX_RESULT_CHARS) + "\n...(已截断)");
                result.setData(map);
            }
        }
    }
}
