package org.example.ai_study_notes.agent.api;

import org.example.ai_study_notes.Pojo.Result;
import org.example.ai_study_notes.agent.auth.UserContext;
import org.example.ai_study_notes.agent.memory.feedback.ReviewFeedbackService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 评审反馈接口：提交"漏了 XX"的评审意见，回流为经验候选。
 */
@RestController
@RequestMapping("/api/agent/memory/review-feedback")
public class ReviewFeedbackController {

    private final ReviewFeedbackService service;

    public ReviewFeedbackController(ReviewFeedbackService service) {
        this.service = service;
    }

    @PostMapping
    public Result<Map<String, Object>> submit(@RequestBody FeedbackRequest request) {
        int saved = service.recordFeedback(UserContext.userId(),
                request.taskType(), request.gap(), request.sourceRef());
        return Result.success(Map.of("saved", saved));
    }

    public record FeedbackRequest(String taskType, String gap, String sourceRef) {
    }
}
