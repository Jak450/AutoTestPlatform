package org.example.ai_study_notes.agent.memory.feedback;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.memory.ExperienceMemoryService;
import org.example.ai_study_notes.agent.memory.episode.EpisodeRecorder;
import org.example.ai_study_notes.agent.memory.experience.MemoryExperience;
import org.example.ai_study_notes.agent.memory.vector.MemoryIndexer;
import org.springframework.stereotype.Service;

/**
 * 评审反馈回流：把"漏了 XX"的评审意见转成经验候选，下次同类任务注入。
 */
@Slf4j
@Service
public class ReviewFeedbackService {

    private final EpisodeRecorder episodeRecorder;
    private final ExperienceMemoryService experienceService;
    private final MemoryIndexer indexer;

    public ReviewFeedbackService(EpisodeRecorder episodeRecorder,
                                 ExperienceMemoryService experienceService,
                                 MemoryIndexer indexer) {
        this.episodeRecorder = episodeRecorder;
        this.experienceService = experienceService;
        this.indexer = indexer;
    }

    public int recordFeedback(Long userId, String taskType, String gap, String sourceRef) {
        episodeRecorder.record(userId, userId, "review_feedback", sourceRef,
                "[评审] taskType=" + taskType + " gap=" + gap);
        String rule = "针对 " + taskType + " 的评审要求：" + gap;
        MemoryExperience exp = experienceService.saveCandidate(
                userId, userId, taskType, rule, sourceRef, 0.95);
        indexer.indexExperience(exp);
        return 1;
    }
}
