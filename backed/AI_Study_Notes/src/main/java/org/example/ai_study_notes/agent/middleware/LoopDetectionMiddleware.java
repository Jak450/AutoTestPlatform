package org.example.ai_study_notes.agent.middleware;

import org.example.ai_study_notes.agent.middleware.Middleware;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 循环检测：同一工具+参数连续调用 3 次即拦截。
 */
@Component
public class LoopDetectionMiddleware implements Middleware {

    private static final int MAX_REPEAT = 3;

    @Override
    public int order() {
        return 7;
    }

    private final Map<Long, Deque<String>> recentByConversation = new ConcurrentHashMap<>();

    @Override
    public Optional<ToolResult> before(String toolName, Map<String, Object> args, ToolContext context) {
        if (context == null || context.getConversationId() == null) {
            return Optional.empty();
        }
        String signature = toolName + ":" + String.valueOf(args);
        Deque<String> recent = recentByConversation.computeIfAbsent(
                context.getConversationId(), k -> new ArrayDeque<>());
        synchronized (recent) {
            if (recent.size() >= MAX_REPEAT) {
                boolean allSame = recent.stream().allMatch(s -> s.equals(signature));
                if (allSame) {
                    return Optional.of(ToolResult.error(toolName, "检测到重复调用循环，已停止",
                            org.example.ai_study_notes.agent.contract.ToolResultMeta.ErrorType.CONFIG,
                            org.example.ai_study_notes.agent.contract.ToolResultMeta.RecommendedNextAction.STOP,
                            System.currentTimeMillis()));
                }
            }
            recent.addLast(signature);
            while (recent.size() > MAX_REPEAT) {
                recent.pollFirst();
            }
        }
        return Optional.empty();
    }
}
