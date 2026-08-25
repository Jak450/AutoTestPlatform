package org.example.ai_study_notes.agent.memory.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RrfFusionTest {

    @Test
    void itemInBothListsRanksHigher() {
        Map<String, Double> scores = RrfFusion.fuse(
                List.of(List.of("a", "b"), List.of("b", "c")), 60);
        assertTrue(scores.get("b") > scores.get("a"));
        assertTrue(scores.get("b") > scores.get("c"));
    }
}
