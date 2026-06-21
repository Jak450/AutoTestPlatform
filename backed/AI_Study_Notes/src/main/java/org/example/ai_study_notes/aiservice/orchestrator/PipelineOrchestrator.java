package org.example.ai_study_notes.aiservice.orchestrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.aiservice.agent.QuestionGenerator;
import org.example.ai_study_notes.aiservice.agent.RequirementAnalyzer;
import org.example.ai_study_notes.aiservice.agent.ResultAnalyzer;
import org.example.ai_study_notes.aiservice.client.AIClient;
import org.example.ai_study_notes.aiservice.client.AIModelConfig;
import org.example.ai_study_notes.aiservice.context.DocContext;
import org.example.ai_study_notes.aiservice.generator.TestCaseGenerator;
import org.example.ai_study_notes.aiservice.skill.SkillLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
public class PipelineOrchestrator {

    @Autowired
    private AIModelConfig aiModelConfig;

    @Autowired
    private SkillLoader skillLoader;

    @Autowired
    private AIClient aiClient;

    @Autowired
    private RequirementAnalyzer requirementAnalyzer;

    @Autowired
    private QuestionGenerator questionGenerator;

    @Autowired
    private List<TestCaseGenerator> testCaseGenerators;

    @Autowired
    private ResultAnalyzer resultAnalyzer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public DocContext executeFullPipeline(String fileName, byte[] fileContent, Integer projectId) throws JsonProcessingException {
        DocContext context = new DocContext();
        context.setFileName(fileName);
        context.setProjectId(projectId);
        context.setRawContent(new String(fileContent, java.nio.charset.StandardCharsets.UTF_8));

        String skillName = skillLoader.resolveDocParserSkill(fileName);
        if (skillName == null) {
            throw new IllegalArgumentException("不支持的文件格式: " + fileName);
        }

        SkillLoader.SkillInfo skill = skillLoader.load(skillName);
        if (skill == null || !skill.isEnabled()) {
            throw new IllegalStateException("缺少解析 skill 或未启用: " + skillName);
        }
        context.setSkillContent(skill.getContent());

        log.info("Pipeline Step 1: 文档解析 - 使用 skill: {}", skillName);
        String parsedDoc = aiClient.chat(
                aiModelConfig.getDocParser(),
                skill.getContent(),
                "请解析以下需求文档，严格按照 SKILL.md 的输出格式返回 JSON：\n\n" + context.getRawContent()
        );
        context.setParsedJson(parsedDoc);

        log.info("Pipeline Step 2: 需求分析");
        Map<String, Object> analysisResult = requirementAnalyzer.analyze(context);
        if (analysisResult != null && analysisResult.containsKey("questions")) {
            context.setAnalysisResult(objectMapper.writeValueAsString(analysisResult));
        }

        return context;
    }

    public void executeFullPipelineStream(String fileName, byte[] fileContent, Integer projectId,
                                           DocContext context, Consumer<String> onEvent) throws JsonProcessingException {
        context.setFileName(fileName);
        context.setProjectId(projectId);
        context.setRawContent(new String(fileContent, java.nio.charset.StandardCharsets.UTF_8));

        String skillName = skillLoader.resolveDocParserSkill(fileName);
        if (skillName == null) {
            throw new IllegalArgumentException("不支持的文件格式: " + fileName);
        }
        SkillLoader.SkillInfo skill = skillLoader.load(skillName);
        if (skill == null || !skill.isEnabled()) {
            throw new IllegalStateException("缺少解析 skill 或未启用: " + skillName);
        }
        context.setSkillContent(skill.getContent());

        onEvent.accept("{\"type\":\"stage\",\"stage\":\"parsing\",\"message\":\"正在解析需求文档...\"}");

        log.info("Pipeline Step 1: 文档解析 - 使用 skill: {}", skillName);
        String parsedDoc = aiClient.chatStream(
                aiModelConfig.getDocParser(),
                skill.getContent(),
                "请解析以下需求文档，严格按照 SKILL.md 的输出格式返回 JSON：\n\n" + context.getRawContent(),
                token -> onEvent.accept("{\"type\":\"token\",\"stage\":\"parsing\",\"content\":\"" + escapeJson(token) + "\"}")
        );
        context.setParsedJson(parsedDoc);

        onEvent.accept("{\"type\":\"stage\",\"stage\":\"analyzing\",\"message\":\"正在分析需求，识别信息缺口...\"}");

        log.info("Pipeline Step 2: 需求分析");
        Map<String, Object> analysisResult = requirementAnalyzer.analyzeStream(context,
                token -> onEvent.accept("{\"type\":\"token\",\"stage\":\"analyzing\",\"content\":\"" + escapeJson(token) + "\"}"));

        if (analysisResult != null && analysisResult.containsKey("questions")) {
            context.setAnalysisResult(objectMapper.writeValueAsString(analysisResult));
        }

        onEvent.accept("{\"type\":\"stage\",\"stage\":\"generating_questions\",\"message\":\"正在生成澄清问题...\"}");

        Map<String, Object> questions = questionGenerator.generateStream(context,
                token -> onEvent.accept("{\"type\":\"token\",\"stage\":\"generating_questions\",\"content\":\"" + escapeJson(token) + "\"}"));

        onEvent.accept("{\"type\":\"done\",\"questions\":" + objectMapper.writeValueAsString(questions.get("questions"))
                + ",\"canGenerate\":" + questions.get("canGenerate") + "}");

        context.setAnalysisResult(objectMapper.writeValueAsString(analysisResult));
    }

    public Map<String, Object> generateQuestions(DocContext context) {
        return questionGenerator.generate(context);
    }

    public DocContext submitAnswers(DocContext context, List<Map<String, String>> answers) {
        for (Map<String, String> answer : answers) {
            context.addQA(answer.get("question"), answer.get("answer"));
        }
        return context;
    }

    public String generateTestCases(DocContext context) {
        for (TestCaseGenerator generator : testCaseGenerators) {
            if (generator.supports("API")) {
                String cases = generator.generate(context);
                context.setGeneratedCasesJson(cases);
                return cases;
            }
        }
        throw new IllegalStateException("未找到可用的用例生成器");
    }

    public String analyzeResult(DocContext context, Integer reportId) {
        return resultAnalyzer.analyze(reportId);
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
