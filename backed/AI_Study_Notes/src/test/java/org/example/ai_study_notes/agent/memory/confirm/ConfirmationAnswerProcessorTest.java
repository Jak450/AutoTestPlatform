package org.example.ai_study_notes.agent.memory.confirm;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ConfirmationAnswerProcessorTest {

    @Test
    void positiveAnswerConfirmsFirstPending() {
        PendingConfirmationService pending = mock(PendingConfirmationService.class);
        ConfirmationAnswerProcessor processor = new ConfirmationAnswerProcessor(pending);

        processor.process(1L, "对");
        verify(pending).confirmNextExperience(1L);
    }

    @Test
    void negativeAnswerWithoutCorrectionRejectsFirstPending() {
        PendingConfirmationService pending = mock(PendingConfirmationService.class);
        ConfirmationAnswerProcessor processor = new ConfirmationAnswerProcessor(pending);

        processor.process(1L, "不对");
        verify(pending).rejectNextExperience(1L);
    }
}
