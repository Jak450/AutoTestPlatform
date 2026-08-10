package org.example.ai_study_notes.agent.knowledge;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.Pojo.Result;
import org.example.ai_study_notes.agent.auth.UserContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 私有测试知识 API（按用户隔离）。
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "includeCandidates", defaultValue = "false") boolean includeCandidates) {
        List<KnowledgeDoc> docs = knowledgeService.list(UserContext.userId(), category, includeCandidates);
        return Result.success(docs.stream().map(this::toMap).toList());
    }

    @GetMapping("/detail")
    public Result<Map<String, Object>> detail(@RequestParam("slug") String slug,
                                              @RequestParam(value = "category", required = false) String category) {
        KnowledgeDoc doc = knowledgeService.get(UserContext.userId(), category, slug);
        Map<String, Object> map = toMap(doc);
        map.put("content", doc.content());
        return Result.success(map);
    }

    @PostMapping("/{slug}/confirm")
    public Result<Map<String, Object>> confirm(@PathVariable("slug") String slug) {
        KnowledgeDoc doc = knowledgeService.confirm(UserContext.userId(), slug);
        return Result.success(toMap(doc));
    }

    @DeleteMapping
    public Result<Void> delete(@RequestParam("title") String title,
                               @RequestParam(value = "category", required = false) String category) {
        knowledgeService.delete(UserContext.userId(), category, title);
        return Result.success();
    }

    @DeleteMapping("/candidates/{slug}")
    public Result<Void> deleteCandidate(@PathVariable("slug") String slug) {
        knowledgeService.deleteCandidate(UserContext.userId(), slug);
        return Result.success();
    }

    private Map<String, Object> toMap(KnowledgeDoc doc) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("slug", doc.slug());
        map.put("category", doc.category());
        map.put("title", doc.title());
        map.put("tags", doc.tags());
        map.put("confirmed", doc.confirmed());
        map.put("snippet", doc.snippet());
        map.put("updatedAt", doc.updatedAt());
        return map;
    }
}
