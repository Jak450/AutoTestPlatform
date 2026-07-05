package org.example.ai_study_notes.aiservice.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class ValidateCaseTool implements Tool {

    private static final Set<String> VALID_METHODS = Set.of("GET", "POST", "PUT", "DELETE", "PATCH");
    private static final Set<String> REQUIRED_FIELDS = Set.of("name", "url", "method");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "validate_case";
    }

    @Override
    public String getDescription() {
        return "校验测试用例的格式和字段。参数：cases（生成的用例 JSON）";
    }

    @Override
    public String execute(String args) {
        try {
            var root = objectMapper.readTree(args);

            var casesNode = root.has("cases") ? root.get("cases") : root;
            if (!casesNode.isArray() || casesNode.isEmpty()) {
                return "{\"valid\": false, \"errors\": [\"用例列表为空\"]}";
            }

            List<String> errors = new ArrayList<>();

            for (int i = 0; i < casesNode.size(); i++) {
                var caseNode = casesNode.get(i);
                String prefix = "用例[" + i + "] ";

                for (String field : REQUIRED_FIELDS) {
                    if (!caseNode.has(field) || caseNode.get(field).isNull()
                            || caseNode.get(field).asText().trim().isEmpty()) {
                        errors.add(prefix + "缺少必填字段: " + field);
                    }
                }

                if (caseNode.has("method")) {
                    String method = caseNode.get("method").asText().toUpperCase();
                    if (!VALID_METHODS.contains(method)) {
                        errors.add(prefix + "method 不合法: " + method + "，必须是 " + VALID_METHODS);
                    }
                }

                if (caseNode.has("url")) {
                    String url = caseNode.get("url").asText();
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        errors.add(prefix + "url 缺少协议头: " + url);
                    }
                }
            }

            boolean valid = errors.isEmpty();
            log.info("ValidateCaseTool 校验{}，错误数: {}", valid ? "通过" : "失败", errors.size());
            return objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
                put("valid", valid);
                if (!valid) put("errors", errors);
            }});
        } catch (Exception e) {
            log.error("ValidateCaseTool 执行失败", e);
            return "{\"valid\": false, \"errors\": [\"JSON 解析失败: " + e.getMessage() + "\"]}";
        }
    }
}
