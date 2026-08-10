package org.example.ai_study_notes.agent.knowledge;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识文档元数据（对应 knowledge/{userId}/{category}/{slug}.md 文件）。
 * 不暴露服务端路径，避免信息泄露。
 */
public record KnowledgeDoc(
        String slug,
        String category,
        String title,
        List<String> tags,
        boolean confirmed,
        String content,
        String snippet,
        LocalDateTime updatedAt) {
}
