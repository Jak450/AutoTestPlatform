package org.example.ai_study_notes.agent.generator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.Pojo.dto.UseCaseUpdateDTO;
import org.example.ai_study_notes.agent.core.AgentAiClient;
import org.example.ai_study_notes.agent.contract.EventType;
import org.example.ai_study_notes.agent.event.ConversationEventStream;
import org.example.ai_study_notes.agent.event.EventStreamService;
import org.example.ai_study_notes.agent.session.AgentMessage;
import org.example.ai_study_notes.agent.session.MessageService;
import org.example.ai_study_notes.agent.template.CaseTemplate;
import org.example.ai_study_notes.agent.template.CaseTemplateService;
import org.example.ai_study_notes.service.UseCaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用例生成/校验/保存服务。
 */
@Slf4j
@Service
public class TestCaseGeneratorService {

    private static final String SYSTEM_PROMPT = """
            你是 AutoTestPlatform 的测试用例生成器。

            根据需求文档生成 API 测试用例，必须只输出一个 JSON 数组，不要包含任何解释、markdown 代码块标记或额外文字。

            每个用例对象字段：
            - pid: 所属项目ID（整数，若未提供项目ID可省略）
            - name: 用例名称（必填）
            - url: 完整接口地址，必须以 http:// 或 https:// 开头（必填）
            - method: GET/POST/PUT/DELETE/PATCH（必填）
            - header: 请求头 JSON 字符串（可选）
            - param: 请求参数 JSON 字符串（可选）
            - assertStr: 断言配置 JSON 字符串（可选）
            - description: 用例描述（可选）

            覆盖正常、异常、边界场景；不要编造接口，需求文档没有的接口不要生成。
            """;

    private final AgentAiClient aiClient;
    private final CaseTemplateService templateService;
    private final UseCaseService useCaseService;
    private final MessageService messageService;
    private final EventStreamService eventStreamService;
    private final ObjectMapper objectMapper;

    public TestCaseGeneratorService(AgentAiClient aiClient,
                                    CaseTemplateService templateService,
                                    UseCaseService useCaseService,
                                    MessageService messageService,
                                    EventStreamService eventStreamService,
                                    ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.templateService = templateService;
        this.useCaseService = useCaseService;
        this.messageService = messageService;
        this.eventStreamService = eventStreamService;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> generateCases(Long userId, Long conversationId,
                                                   String docText, Long templateId, Integer projectId) {
        CaseTemplate template = null;
        if (templateId != null) {
            template = templateService.getOwned(userId, templateId);
        }
        if (template == null) {
            template = templateService.getActive(conversationId);
        }
        StringBuilder system = new StringBuilder(SYSTEM_PROMPT);
        if (template != null) {
            system.append("\n\n=== 当前使用的用例模板 ===\n")
                    .append("模板名称: ").append(template.getName()).append('\n')
                    .append("caseShape: ").append(template.getCaseShape()).append('\n')
                    .append("coverageRules: ").append(template.getCoverageRules()).append('\n')
                    .append("assertRules: ").append(template.getAssertRules()).append('\n')
                    .append("examples: ").append(template.getExamples());
        }
        StringBuilder user = new StringBuilder("需求文档内容：\n").append(docText);
        if (projectId != null) {
            user.append("\n\n目标项目ID: ").append(projectId);
        }
        // 模型偶发输出被截断/非法 JSON，做有限重试；生成预算已单独放大（generation-max-tokens）
        for (int attempt = 1; ; attempt++) {
            String raw = aiClient.chat(system.toString(), user.toString());
            try {
                List<Map<String, Object>> cases = parseJsonArray(raw);
                appendCasePreview(conversationId, cases, template == null ? null : template.getId(), projectId);
                return cases;
            } catch (Exception e) {
                if (attempt >= 3) {
                    throw new IllegalStateException("模型多次返回不合法用例 JSON: " + e.getMessage(), e);
                }
                log.warn("generate_cases 第 {} 次输出解析失败，重试: {}", attempt, e.getMessage());
                user.append("\n\n【重要】上一次输出不是完整合法的 JSON 数组。请重新输出：只输出 JSON 数组本身，"
                        + "不要任何解释或 markdown 代码块标记，所有字符串必须完整闭合，不要省略字段。");
            }
        }
    }

    public List<String> validateCases(List<Map<String, Object>> cases) {
        List<String> errors = new ArrayList<>();
        if (cases == null || cases.isEmpty()) {
            errors.add("用例列表为空");
            return errors;
        }
        for (int i = 0; i < cases.size(); i++) {
            Map<String, Object> c = cases.get(i);
            String prefix = "用例[" + (i + 1) + "]";
            if (c.get("name") == null || String.valueOf(c.get("name")).isBlank()) {
                errors.add(prefix + "缺少 name");
            }
            String url = c.get("url") == null ? null : String.valueOf(c.get("url"));
            if (url == null || url.isBlank()) {
                errors.add(prefix + "缺少 url");
            } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
                errors.add(prefix + " url 必须以 http(s):// 开头");
            }
            String method = c.get("method") == null ? null : String.valueOf(c.get("method")).toUpperCase();
            if (method == null || !List.of("GET", "POST", "PUT", "DELETE", "PATCH").contains(method)) {
                errors.add(prefix + " method 必须是 GET/POST/PUT/DELETE/PATCH");
            } else {
                c.put("method", method);
            }
        }
        return errors;
    }

    @Transactional(rollbackFor = Exception.class)
    public int saveCases(List<Map<String, Object>> cases, Integer projectId) {
        List<String> errors = validateCases(cases);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("用例校验失败: " + String.join("; ", errors));
        }
        int saved = 0;
        for (Map<String, Object> c : cases) {
            Integer pid = projectId;
            if (c.get("pid") instanceof Number n) {
                pid = n.intValue();
            }
            if (pid == null) {
                throw new IllegalArgumentException("缺少目标项目ID（pid/projectId）");
            }
            UseCaseUpdateDTO dto = UseCaseUpdateDTO.builder()
                    .pid(pid)
                    .name(String.valueOf(c.get("name")))
                    .url(String.valueOf(c.get("url")))
                    .method(String.valueOf(c.get("method")))
                    .header(c.get("header") == null ? null : String.valueOf(c.get("header")))
                    .param(c.get("param") == null ? null : String.valueOf(c.get("param")))
                    .assertStr(c.get("assertStr") == null ? null : String.valueOf(c.get("assertStr")))
                    .desc(c.get("description") == null ? null : String.valueOf(c.get("description")))
                    .build();
            useCaseService.addUseCase(dto);
            saved++;
        }
        return saved;
    }

    private void appendCasePreview(Long conversationId, List<Map<String, Object>> cases,
                                   Long templateId, Integer projectId) {
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("count", cases.size());
            meta.put("templateId", templateId);
            meta.put("projectId", projectId);
            AgentMessage message = messageService.append(conversationId, "assistant", "case_preview",
                    objectMapper.writeValueAsString(cases), objectMapper.writeValueAsString(meta));
            ConversationEventStream stream = eventStreamService.getOrCreate(conversationId);
            stream.emit(EventType.MESSAGE_START.value(), Map.of(
                    "messageId", message.getId(), "role", "assistant", "type", "case_preview",
                    "count", cases.size()));
            stream.emit(EventType.MESSAGE_UPDATE.value(), Map.of(
                    "messageId", message.getId(), "delta", "生成 " + cases.size() + " 条用例草稿",
                    "type", "case_preview", "cases", cases));
            stream.emit(EventType.MESSAGE_END.value(), Map.of(
                    "messageId", message.getId(), "type", "case_preview"));
        } catch (Exception e) {
            log.warn("用例预览消息写入失败: {}", e.getMessage());
        }
    }

    private List<Map<String, Object>> parseJsonArray(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("模型未返回文本内容（输出为空）");
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            int first = cleaned.indexOf('\n');
            int last = cleaned.lastIndexOf("```");
            if (first >= 0 && last > first) {
                cleaned = cleaned.substring(first + 1, last).trim();
            }
        }
        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }
        try {
            return objectMapper.readValue(cleaned, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("模型返回的不是合法用例 JSON: " + e.getMessage());
        }
    }
}
