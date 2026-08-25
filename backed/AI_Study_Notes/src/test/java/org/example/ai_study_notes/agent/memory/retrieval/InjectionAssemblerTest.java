package org.example.ai_study_notes.agent.memory.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InjectionAssemblerTest {

    @Test
    void respectsBudgetAndOrdersByScore() {
        Map<String, Double> scores = Map.of("x", 0.9, "y", 0.8);
        Map<String, String> content = Map.of("x", "xxxxx", "y", "yy");
        String out = InjectionAssembler.assemble(scores, content, 6, List.of());
        assertTrue(out.length() <= 6);
        assertTrue(out.startsWith("xxxxx"));
    }
}
