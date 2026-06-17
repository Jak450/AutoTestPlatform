package org.example.ai_study_notes.aiservice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.Pojo.Result;
import org.example.ai_study_notes.Pojo.dto.AiRequirementDTO;
import org.example.ai_study_notes.aiservice.context.DocContext;
import org.example.ai_study_notes.aiservice.orchestrator.PipelineOrchestrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/ai")
public class AIController {

    @Autowired
    private PipelineOrchestrator orchestrator;

    @Autowired
    private SessionManager sessionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_QUESTIONS = 5;

    private List<Map<String, Object>> truncateQuestions(List<Map<String, Object>> questions) {
        if (questions == null) return List.of();
        if (questions.size() <= MAX_QUESTIONS) return questions;
        log.warn("AI 返回了 {} 个问题，截断为 {} 个", questions.size(), MAX_QUESTIONS);
        return new ArrayList<>(questions.subList(0, MAX_QUESTIONS));
    }

    @PostMapping("/analyze-requirement")
    public Result<Map<String, Object>> analyzeRequirement(@RequestBody AiRequirementDTO dto) {
        try {
            DocContext context = orchestrator.executeFullPipeline(
                    dto.getFileName(), dto.getContent().getBytes(StandardCharsets.UTF_8), dto.getProjectId());
            String sessionId = sessionManager.create(context);

            Map<String, Object> questions = orchestrator.generateQuestions(context);
            sessionManager.update(sessionId, context);

            Map<String, Object> result = objectMapper.readValue(
                    context.getAnalysisResult(), new TypeReference<Map<String, Object>>() {});
            result.put("sessionId", sessionId);
            result.put("questions", truncateQuestions((List<Map<String, Object>>) questions.get("questions")));
            result.put("canGenerate", questions.get("canGenerate"));
            return Result.<Map<String, Object>>success(result);
        } catch (Exception e) {
            log.error("需求分析失败", e);
            return Result.<Map<String, Object>>error("需求分析失败: " + e.getMessage());
        }
    }

    @PostMapping(value = "/analyze-requirement-stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter analyzeRequirementStream(@RequestBody AiRequirementDTO dto) {
        SseEmitter emitter = new SseEmitter(300000L);

        CompletableFuture.runAsync(() -> {
            try {
                DocContext context = new DocContext();
                context.setFileName(dto.getFileName());
                context.setProjectId(dto.getProjectId());
                context.setRawContent(dto.getContent());

                orchestrator.executeFullPipelineStream(
                        dto.getFileName(),
                        dto.getContent().getBytes(StandardCharsets.UTF_8),
                        dto.getProjectId(),
                        context,
                        event -> {
                            try {
                                emitter.send(SseEmitter.event().data(event));
                            } catch (Exception e) {
                                log.error("SSE send error", e);
                            }
                        }
                );

                String sessionId = sessionManager.create(context);
                emitter.send(SseEmitter.event().data("{\"type\":\"session\",\"sessionId\":\"" + sessionId + "\"}"));
                emitter.complete();

            } catch (Exception e) {
                log.error("流式分析失败", e);
                try {
                    emitter.send(SseEmitter.event().data("{\"type\":\"error\",\"message\":\"" + e.getMessage() + "\"}"));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }

    @PostMapping("/submit-answers")
    public Result<Map<String, Object>> submitAnswers(
            @RequestParam("sessionId") String sessionId,
            @RequestBody List<Map<String, String>> answers) {
        DocContext context = sessionManager.get(sessionId);
        if (context == null) {
            return Result.<Map<String, Object>>error("会话已过期，请重新上传");
        }

        context = orchestrator.submitAnswers(context, answers);

        Map<String, Object> questions = orchestrator.generateQuestions(context);
        sessionManager.update(sessionId, context);

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("questions", truncateQuestions((List<Map<String, Object>>) questions.get("questions")));
        result.put("canGenerate", questions.get("canGenerate"));
        return Result.<Map<String, Object>>success(result);
    }

    @PostMapping("/generate-cases")
    public Result<String> generateCases(@RequestParam("sessionId") String sessionId) {
        DocContext context = sessionManager.get(sessionId);
        if (context == null) {
            return Result.<String>error("会话已过期，请重新上传");
        }

        try {
            String cases = orchestrator.generateTestCases(context);
            sessionManager.remove(sessionId);
            return Result.<String>success(cases);
        } catch (Exception e) {
            log.error("用例生成失败", e);
            return Result.<String>error("用例生成失败: " + e.getMessage());
        }
    }

    @PostMapping("/analyze-result/{reportId}")
    public Result<String> analyzeResult(@PathVariable("reportId") Integer reportId) {
        try {
            String analysis = orchestrator.analyzeResult(null, reportId);
            return Result.<String>success(analysis);
        } catch (Exception e) {
            log.error("结果分析失败", e);
            return Result.<String>error("结果分析失败: " + e.getMessage());
        }
    }
}
