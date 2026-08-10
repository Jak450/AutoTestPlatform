package org.example.ai_study_notes.agent.middleware;

import org.example.ai_study_notes.agent.middleware.Middleware;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.springframework.stereotype.Component;

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
        }
    }
}
