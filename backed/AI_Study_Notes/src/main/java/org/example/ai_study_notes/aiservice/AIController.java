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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/ai")
public class AIController {

    @Autowired
    private PipelineOrchestrator orchestrator;

    @Autowired
    private SessionManager sessionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
            result.put("questions", questions.get("questions"));
            result.put("canGenerate", questions.get("canGenerate"));
            return Result.<Map<String, Object>>success(result);
        } catch (Exception e) {
            log.error("需求分析失败", e);
            return Result.<Map<String, Object>>error("需求分析失败: " + e.getMessage());
        }
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
        result.put("questions", questions.get("questions"));
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
    public Result<String> analyzeResult(@PathVariable Integer reportId) {
        try {
            String analysis = orchestrator.analyzeResult(null, reportId);
            return Result.<String>success(analysis);
        } catch (Exception e) {
            log.error("结果分析失败", e);
            return Result.<String>error("结果分析失败: " + e.getMessage());
        }
    }
}