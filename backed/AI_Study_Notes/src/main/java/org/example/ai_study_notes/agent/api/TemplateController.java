package org.example.ai_study_notes.agent.api;

import lombok.Data;
import org.example.ai_study_notes.Pojo.Result;
import org.example.ai_study_notes.agent.auth.UserContext;
import org.example.ai_study_notes.agent.template.CaseTemplate;
import org.example.ai_study_notes.agent.template.CaseTemplateService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用例模板 API。
 */
@RestController
@RequestMapping("/api/agent/templates")
public class TemplateController {

    private final CaseTemplateService templateService;

    public TemplateController(CaseTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        List<CaseTemplate> templates = templateService.list(UserContext.userId());
        List<Map<String, Object>> result = new ArrayList<>();
        for (CaseTemplate template : templates) {
            result.add(templateService.toMap(template));
        }
        return Result.success(result);
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody TemplateRequest request) {
        CaseTemplate template = templateService.create(
                UserContext.userId(),
                request.getName(),
                request.getDescription(),
                request.getCaseShape(),
                request.getCoverageRules(),
                request.getAssertRules(),
                request.getExamples());
        return Result.success(templateService.toMap(template));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        templateService.delete(UserContext.userId(), id);
        return Result.success();
    }

    @Data
    public static class TemplateRequest {
        private String name;
        private String description;
        private String caseShape;
        private String coverageRules;
        private String assertRules;
        private String examples;
    }
}
