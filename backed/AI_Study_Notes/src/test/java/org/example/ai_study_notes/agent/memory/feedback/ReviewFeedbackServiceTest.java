package org.example.ai_study_notes.agent.memory.feedback;

import org.example.ai_study_notes.agent.memory.ExperienceMemoryService;
import org.example.ai_study_notes.agent.memory.episode.EpisodeRecorder;
import org.example.ai_study_notes.agent.memory.vector.MemoryIndexer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReviewFeedbackServiceTest {

    @Test
    void feedbackBecomesExperienceCandidate() {
        EpisodeRecorder recorder = mock(EpisodeRecorder.class);
        ExperienceMemoryService experienceService = mock(ExperienceMemoryService.class);
        MemoryIndexer indexer = mock(MemoryIndexer.class);
        ReviewFeedbackService service =
                new ReviewFeedbackService(recorder, experienceService, indexer);

        int saved = service.recordFeedback(1L, "test_case_extraction", "漏了兼容性测试点", "评审#5");

        assertEquals(1, saved);
        verify(recorder).record(eq(1L), eq(1L), eq("review_feedback"), eq("评审#5"), any());
        verify(experienceService).saveCandidate(eq(1L), eq(1L), eq("test_case_extraction"),
                eq("针对 test_case_extraction 的评审要求：漏了兼容性测试点"), any(), anyDouble());
    }
}
