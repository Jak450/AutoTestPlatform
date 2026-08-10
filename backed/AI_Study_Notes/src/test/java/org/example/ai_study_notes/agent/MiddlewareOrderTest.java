package org.example.ai_study_notes.agent;

import org.example.ai_study_notes.agent.middleware.GuardrailMiddleware;
import org.example.ai_study_notes.agent.middleware.InputSanitizationMiddleware;
import org.example.ai_study_notes.agent.middleware.LoopDetectionMiddleware;
import org.example.ai_study_notes.agent.middleware.Middleware;
import org.example.ai_study_notes.agent.middleware.MiddlewareChain;
import org.example.ai_study_notes.agent.middleware.ReadBeforeWriteMiddleware;
import org.example.ai_study_notes.agent.middleware.ToolOutputBudgetMiddleware;
import org.example.ai_study_notes.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 中间件顺序为强约束，必须被测试钉住（MIDDLE-1）。
 */
class MiddlewareOrderTest {

    @Test
    void middlewareOrderIsPinned() {
        ToolRegistry registry = new ToolRegistry();
        List<Middleware> shuffled = List.of(
                new GuardrailMiddleware(registry),
                new LoopDetectionMiddleware(),
                new InputSanitizationMiddleware(),
                new ReadBeforeWriteMiddleware(registry),
                new ToolOutputBudgetMiddleware());
        MiddlewareChain chain = new MiddlewareChain(shuffled);
        List<String> actual = chain.middlewares().stream()
                .map(m -> m.getClass().getSimpleName())
                .toList();
        assertEquals(List.of(
                InputSanitizationMiddleware.class.getSimpleName(),
                ToolOutputBudgetMiddleware.class.getSimpleName(),
                ReadBeforeWriteMiddleware.class.getSimpleName(),
                LoopDetectionMiddleware.class.getSimpleName(),
                GuardrailMiddleware.class.getSimpleName()), actual);
    }
}
