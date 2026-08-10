package org.example.ai_study_notes.agent.middleware;

import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 中间件链：before 按注册顺序（首个非空即拦截），after 逆序分发。
 */
@Component
public class MiddlewareChain {

    private final List<Middleware> middlewares;

    public MiddlewareChain(List<Middleware> middlewares) {
        this.middlewares = middlewares.stream()
                .sorted(java.util.Comparator.comparingInt(Middleware::order))
                .toList();
    }

    public Optional<ToolResult> before(String toolName, Map<String, Object> args, ToolContext context) {
        for (Middleware middleware : middlewares) {
            Optional<ToolResult> intercepted = middleware.before(toolName, args, context);
            if (intercepted.isPresent()) {
                return intercepted;
            }
        }
        return Optional.empty();
    }

    public void after(String toolName, Map<String, Object> args, ToolResult result, ToolContext context) {
        for (int i = middlewares.size() - 1; i >= 0; i--) {
            middlewares.get(i).after(toolName, args, result, context);
        }
    }

    public List<Middleware> middlewares() {
        return middlewares;
    }
}
