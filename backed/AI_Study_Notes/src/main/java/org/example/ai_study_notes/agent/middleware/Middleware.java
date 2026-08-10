package org.example.ai_study_notes.agent.middleware;

import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolResult;

import java.util.Map;
import java.util.Optional;

/**
 * 工具中间件：before 返回非空即拦截，after 按注册逆序执行。
 */
public interface Middleware {

    /**
     * 执行顺序（小优先），顺序为强约束，由 MiddlewareChain 排序并测试钉住。
     */
    default int order() {
        return 100;
    }

    default Optional<ToolResult> before(String toolName, Map<String, Object> args, ToolContext context) {
        return Optional.empty();
    }

    default void after(String toolName, Map<String, Object> args, ToolResult result, ToolContext context) {
    }
}
