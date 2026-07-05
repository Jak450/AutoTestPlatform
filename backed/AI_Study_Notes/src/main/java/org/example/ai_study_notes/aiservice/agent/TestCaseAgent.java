package org.example.ai_study_notes.aiservice.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.aiservice.agent.tool.Tool;
import org.example.ai_study_notes.aiservice.client.AIClient;
import org.example.ai_study_notes.aiservice.client.AIModelConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class TestCaseAgent {

    private static final int MAX_ROUNDS = 10;
    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[\\s\\S]*\\}");

    @Autowired
    private AIClient aiClient;
    @Autowired
    private AIModelConfig aiModelConfig;
    @Autowired
    private List<Tool> tools;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                你是测试用例生成 Agent，采用 ReAct（Reasoning + Acting）模式工作。

                每轮你必须输出且仅输出一个 JSON（不要 markdown 代码块）：

                调用工具：
                {"thought": "你的推理过程", "action": "call_tool", "tool": "工具名", "args": {参数}}

                返回问题给用户：
                {"thought": "你的推理过程", "action": "finish_questions", "questions": [...]}

                返回最终用例：
                {"thought": "你的推理过程", "action": "finish_cases", "cases": [...]}

                thought 字段必填，写出你的推理：当前状态是什么、为什么这么做、预期结果。

                可用工具：
                """);
        for (Tool tool : tools) {
            sb.append("- ").append(tool.getName()).append(": ").append(tool.getDescription()).append("\n");
        }
        sb.append("""

                决策规则：
                1. 收到文档 → 先调 parse_document 解析
                2. 解析完 → 调 analyze_gap 分析缺口
                3. 有缺口 → 调 ask_user 生成问题，然后 finish_questions 返回给用户
                4. 无缺口或用户已回答 → 调 generate_case 生成用例
                5. 生成完 → 调 validate_case 校验
                6. 校验失败 → 重新调 generate_case（最多 3 次）
                7. 校验通过 → finish_cases 返回最终用例
                """);
        return sb.toString();
    }

    public Map<String, Object> run(String userInput, String qaHistory) {
        List<Map<String, String>> messages = new ArrayList<>();

        String userContent = "用户输入：\n" + userInput;
        if (qaHistory != null && !qaHistory.isEmpty()) {
            userContent += "\n\n用户回答：\n" + qaHistory;
        }

        messages.add(Map.of("role", "system", "content", buildSystemPrompt()));
        messages.add(Map.of("role", "user", "content", userContent));

        log.info("Agent 开始执行，userInput 长度: {}", userInput.length());

        for (int round = 0; round < MAX_ROUNDS; round++) {
            log.info("Agent 第 {} 轮", round + 1);

            String aiResponse = aiClient.chatWithHistory(aiModelConfig.getDocParser(), messages);
            messages.add(Map.of("role", "assistant", "content", aiResponse));

            String json = extractJson(aiResponse);
            if (json == null) {
                log.warn("Agent 第 {} 轮无法解析 JSON，重试", round + 1);
                messages.add(Map.of("role", "user", "content", "请只输出 JSON，不要其他内容。"));
                continue;
            }

            try {
                var decision = objectMapper.readTree(json);
                String thought = decision.has("thought") ? decision.get("thought").asText() : "";
                String action = decision.has("action") ? decision.get("action").asText() : "";

                if (!thought.isEmpty()) {
                    log.info("Agent Thought: {}", thought);
                }

                switch (action) {
                    case "call_tool" -> {
                        String toolName = decision.get("tool").asText();
                        String toolArgs = decision.has("args") ? decision.get("args").toString() : "{}";

                        Tool tool = findTool(toolName);
                        if (tool == null) {
                            messages.add(Map.of("role", "user", "content", "工具不存在: " + toolName + "，请从可用工具中选择。"));
                            continue;
                        }

                        log.info("Agent Action: {}", toolName);
                        String toolResult = tool.execute(toolArgs);
                        log.info("Agent Observation 长度: {}", toolResult.length());
                        messages.add(Map.of("role", "user", "content",
                                "Observation: " + toolResult + "\n\n基于以上观察，请继续推理并决定下一步行动。"));
                    }

                    case "finish_questions" -> {
                        log.info("Agent 结束并返回问题给用户");
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("type", "questions");
                        result.put("questions", decision.get("questions"));
                        return result;
                    }

                    case "finish_cases" -> {
                        log.info("Agent 结束并返回最终用例");
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("type", "cases");
                        result.put("cases", decision.get("cases"));
                        return result;
                    }

                    default -> {
                        log.warn("Agent 未知 action: {}", action);
                        messages.add(Map.of("role", "user", "content", "未知 action: " + action + "，请使用 call_tool、finish_questions 或 finish_cases。"));
                    }
                }
            } catch (Exception e) {
                log.error("Agent 第 {} 轮解析失败", round + 1, e);
                messages.add(Map.of("role", "user", "content", "JSON 解析失败，请重新输出。"));
            }
        }

        log.warn("Agent 达到最大轮数 {}", MAX_ROUNDS);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "error");
        result.put("message", "Agent 超过最大轮数限制");
        return result;
    }

    private Tool findTool(String name) {
        return tools.stream().filter(t -> t.getName().equals(name)).findFirst().orElse(null);
    }

    private String extractJson(String text) {
        Matcher matcher = JSON_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }
}
