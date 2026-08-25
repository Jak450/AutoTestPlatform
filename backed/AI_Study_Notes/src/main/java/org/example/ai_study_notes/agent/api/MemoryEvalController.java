package org.example.ai_study_notes.agent.api;

import org.example.ai_study_notes.Pojo.Result;
import org.example.ai_study_notes.agent.auth.UserContext;
import org.example.ai_study_notes.agent.memory.retrieval.MemoryRetriever;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 记忆检索调试/评测接口：供离线 golden set 脚本计算 Recall@K / MRR。
 */
@RestController
@RequestMapping("/api/agent/memory")
public class MemoryEvalController {

    private final MemoryRetriever retriever;

    public MemoryEvalController(MemoryRetriever retriever) {
        this.retriever = retriever;
    }

    @GetMapping("/retrieve")
    public Result<Map<String, Object>> retrieve(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") Long workspaceId,
            @RequestParam(defaultValue = "5") int topK) {
        Long userId = UserContext.userId();
        long ws = workspaceId == null || workspaceId == 0 ? userId : workspaceId;
        List<MemoryRetriever.RankedItem> items = retriever.retrieve(ws, userId, query, topK);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("query", query);
        data.put("items", items.stream().map(item -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", item.type());
            m.put("id", item.id());
            m.put("content", item.content());
            m.put("score", item.score());
            return m;
        }).toList());
        return Result.success(data);
    }
}
