package org.example.ai_study_notes.agent.api;

import lombok.Data;
import org.example.ai_study_notes.Pojo.Result;
import org.example.ai_study_notes.agent.auth.UserContext;
import org.example.ai_study_notes.agent.memory.MemoryEntry;
import org.example.ai_study_notes.agent.memory.MemoryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 长期记忆 API。
 */
@RestController
@RequestMapping("/api/agent/memory")
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(value = "query", required = false) String query) {
        List<MemoryEntry> entries = memoryService.list(UserContext.userId(), query);
        List<Map<String, Object>> result = new ArrayList<>();
        for (MemoryEntry entry : entries) {
            result.add(toMap(entry));
        }
        return Result.success(result);
    }

    @PostMapping
    public Result<Map<String, Object>> save(@RequestBody SaveMemoryRequest request) {
        MemoryEntry entry = memoryService.save(
                UserContext.userId(),
                request.getKey(),
                request.getContent(),
                request.getTags(),
                request.getOverwrite(),
                request.getSourceSessionId());
        return Result.success(toMap(entry));
    }

    @DeleteMapping
    public Result<Void> delete(@RequestParam("key") String key) {
        memoryService.delete(UserContext.userId(), key);
        return Result.success();
    }

    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable("id") Long id) {
        memoryService.confirm(UserContext.userId(), id);
        return Result.success();
    }

    private Map<String, Object> toMap(MemoryEntry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entry.getId());
        map.put("key", entry.getMemKey());
        map.put("content", entry.getContentMd());
        map.put("tags", entry.getTags());
        map.put("confirmed", entry.getConfirmed());
        map.put("version", entry.getVersion());
        map.put("updatedAt", entry.getUpdatedAt());
        return map;
    }

    @Data
    public static class SaveMemoryRequest {
        private String key;
        private String content;
        private List<String> tags;
        private Boolean overwrite;
        private Long sourceSessionId;
    }
}
