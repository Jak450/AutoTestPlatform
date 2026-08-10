package org.example.ai_study_notes.agent.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.memory.MemoryEntry;
import org.example.ai_study_notes.agent.memory.MemoryMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 一次性迁移：把已确认的 DB 记忆（namespace=preference, confirmed=1）导出为 MD 知识文档。
 * 按用户执行，每个用户完成后写入标记文件，避免重复迁移。
 */
@Slf4j
@Component
public class KnowledgeMigration implements CommandLineRunner {

    private static final String MARKER = ".migrated-v1";

    private final MemoryMapper memoryMapper;
    private final KnowledgeService knowledgeService;

    public KnowledgeMigration(MemoryMapper memoryMapper, KnowledgeService knowledgeService) {
        this.memoryMapper = memoryMapper;
        this.knowledgeService = knowledgeService;
    }

    @Override
    public void run(String... args) {
        try {
            List<MemoryEntry> confirmed = memoryMapper.selectList(new LambdaQueryWrapper<MemoryEntry>()
                    .eq(MemoryEntry::getConfirmed, 1)
                    .eq(MemoryEntry::getNamespace, "preference"));
            if (confirmed.isEmpty()) {
                log.info("知识迁移：无已确认记忆，跳过");
                return;
            }
            Map<Long, List<MemoryEntry>> byUser = new LinkedHashMap<>();
            for (MemoryEntry entry : confirmed) {
                byUser.computeIfAbsent(entry.getUserId(), k -> new ArrayList<>()).add(entry);
            }
            int migrated = 0;
            for (Map.Entry<Long, List<MemoryEntry>> userEntry : byUser.entrySet()) {
                Long userId = userEntry.getKey();
                Path marker = knowledgeService.userRoot(userId).resolve(MARKER);
                if (Files.exists(marker)) {
                    continue;
                }
                for (MemoryEntry memory : userEntry.getValue()) {
                    try {
                        knowledgeService.save(userId, memory.getMemKey(), memory.getContentMd(),
                                "记忆迁移", parseTags(memory.getTags()), true);
                        migrated++;
                    } catch (Exception e) {
                        log.warn("记忆迁移失败 userId={} key={}: {}", userId, memory.getMemKey(), e.getMessage());
                    }
                }
                try {
                    Files.createDirectories(marker.getParent());
                    Files.writeString(marker, LocalDateTime.now().toString(), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    log.warn("知识迁移标记写入失败 userId={}", userId);
                }
            }
            log.info("知识迁移完成：共导出 {} 条已确认记忆为 MD 知识文档", migrated);
        } catch (Exception e) {
            log.warn("知识迁移执行失败: {}", e.getMessage());
        }
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Stream.of(tags.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
