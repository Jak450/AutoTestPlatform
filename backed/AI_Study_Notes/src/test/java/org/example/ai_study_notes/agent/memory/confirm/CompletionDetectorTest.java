package org.example.ai_study_notes.agent.memory.confirm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompletionDetectorTest {

    private final CompletionDetector detector = new CompletionDetector();

    @Test
    void detectsCompletionPhrases() {
        assertTrue(detector.isCompletionSignal("这个需求分析完成了"));
        assertTrue(detector.isCompletionSignal("先这样吧"));
        assertFalse(detector.isCompletionSignal("继续分析购物模块"));
    }
}
