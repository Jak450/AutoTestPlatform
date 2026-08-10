package org.example.ai_study_notes.agent.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 长期记忆服务：按用户隔离，同 key 覆盖升级版本。
 */
@Service
public class MemoryService {

    private static final int MAX_INJECT = 5;

    private final MemoryMapper memoryMapper;

    public MemoryService(MemoryMapper memoryMapper) {
        this.memoryMapper = memoryMapper;
    }

    public List<MemoryEntry> list(Long userId, String query) {
        LambdaQueryWrapper<MemoryEntry> wrapper = new LambdaQueryWrapper<MemoryEntry>()
                .eq(MemoryEntry::getUserId, userId)
                .orderByDesc(MemoryEntry::getUpdatedAt);
        if (query != null && !query.isBlank()) {
            wrapper.and(w -> w
                    .like(MemoryEntry::getMemKey, query)
                    .or().like(MemoryEntry::getContentMd, query)
                    .or().like(MemoryEntry::getTags, query));
        }
        return memoryMapper.selectList(wrapper);
    }

    public MemoryEntry findByKey(Long userId, String key) {
        return memoryMapper.selectOne(new LambdaQueryWrapper<MemoryEntry>()
                .eq(MemoryEntry::getUserId, userId)
                .eq(MemoryEntry::getMemKey, key));
    }

    public MemoryEntry save(Long userId, String key, String content, List<String> tags,
                            Boolean overwrite, Long sourceSessionId) {
        if (key == null || key.isBlank() || content == null || content.isBlank()) {
            throw new IllegalArgumentException("记忆 key 和 content 不能为空");
        }
        MemoryEntry existing = findByKey(userId, key);
        if (existing != null) {
            if (!Boolean.TRUE.equals(overwrite)) {
                throw new IllegalArgumentException("记忆已存在，覆盖需要用户确认（overwrite=true）");
            }
            existing.setContentMd(content);
            existing.setTags(toJsonTags(tags));
            existing.setVersion((existing.getVersion() == null ? 1 : existing.getVersion()) + 1);
            existing.setConfirmed(1);
            existing.setSourceSessionId(sourceSessionId);
            memoryMapper.updateById(existing);
            return existing;
        }
        MemoryEntry entry = MemoryEntry.builder()
                .userId(userId)
                .scope("user")
                .namespace("preference")
                .memKey(key)
                .contentMd(content)
                .tags(toJsonTags(tags))
                .confidence("medium")
                .confirmed(1)
                .sourceSessionId(sourceSessionId)
                .version(1)
                .build();
        memoryMapper.insert(entry);
        return entry;
    }

    public void delete(Long userId, String key) {
        MemoryEntry existing = findByKey(userId, key);
        if (existing == null) {
            throw new IllegalArgumentException("记忆不存在");
        }
        memoryMapper.deleteById(existing.getId());
    }

    /**
     * 合并同主题偏好：把新内容并入已有记忆（内容追加 + 版本 +1），减少冗余主题。
     */
    public MemoryEntry merge(Long userId, String targetKey, String newContent) {
        if (targetKey == null || targetKey.isBlank() || newContent == null || newContent.isBlank()) {
            throw new IllegalArgumentException("合并目标与内容不能为空");
        }
        MemoryEntry existing = findByKey(userId, targetKey);
        if (existing == null) {
            throw new IllegalArgumentException("目标记忆不存在: " + targetKey);
        }
        String merged = existing.getContentMd() == null ? "" : existing.getContentMd();
        if (!merged.contains(newContent)) {
            merged = merged.isBlank() ? newContent : merged + "；" + newContent;
            if (merged.length() > 500) {
                merged = merged.substring(0, 500) + "…";
            }
        }
        existing.setContentMd(merged);
        existing.setVersion((existing.getVersion() == null ? 1 : existing.getVersion()) + 1);
        existing.setConfirmed(1);
        memoryMapper.updateById(existing);
        return existing;
    }

    /**
     * 保存未确认的自动提炼候选（confirmed=0，不参与注入）。
     */
    public MemoryEntry saveCandidate(Long userId, String key, String content) {
        if (findByKey(userId, key) != null) {
            return null;
        }
        MemoryEntry entry = MemoryEntry.builder()
                .userId(userId)
                .scope("user")
                .namespace("candidate")
                .memKey(key)
                .contentMd(content)
                .tags(toJsonTags(List.of("auto")))
                .confidence("medium")
                .confirmed(0)
                .version(1)
                .build();
        memoryMapper.insert(entry);
        return entry;
    }

    public void confirm(Long userId, Long memoryId) {
        MemoryEntry entry = memoryMapper.selectById(memoryId);
        if (entry == null || !entry.getUserId().equals(userId)) {
            throw new IllegalArgumentException("记忆不存在或无权访问");
        }
        MemoryEntry update = new MemoryEntry();
        update.setId(entry.getId());
        update.setConfirmed(1);
        update.setNamespace("preference");
        memoryMapper.updateById(update);
    }

    /**
     * 供上下文注入：返回最近确认的 Top-N 条记忆。
     */
    public List<String> injectable(Long userId) {
        return injectable(userId, "");
    }

    /**
     * 按查询相关性返回 Top-N 已确认记忆（简单词频打分），总预算 2KB。
     */
    public List<String> injectable(Long userId, String query) {
        List<MemoryEntry> entries = memoryMapper.selectList(new LambdaQueryWrapper<MemoryEntry>()
                .eq(MemoryEntry::getUserId, userId)
                .eq(MemoryEntry::getConfirmed, 1)
                .orderByDesc(MemoryEntry::getUpdatedAt)
                .last("limit 100"));
        Map<Long, Integer> scores = new LinkedHashMap<>();
        List<String> terms = tokenize(query);
        for (MemoryEntry entry : entries) {
            int score = 0;
            String haystack = (entry.getMemKey() + " " + entry.getContentMd() + " " + entry.getTags()).toLowerCase();
            for (String term : terms) {
                if (haystack.contains(term)) {
                    score++;
                }
            }
            scores.put(entry.getId(), score);
        }
        entries.sort((a, b) -> {
            int byScore = Integer.compare(scores.getOrDefault(b.getId(), 0), scores.getOrDefault(a.getId(), 0));
            return byScore != 0 ? byScore : b.getUpdatedAt().compareTo(a.getUpdatedAt());
        });
        List<String> result = new ArrayList<>();
        int budget = 2048;
        for (MemoryEntry entry : entries) {
            if (result.size() >= MAX_INJECT) {
                break;
            }
            String line = entry.getMemKey() + ": " + entry.getContentMd();
            budget -= line.length();
            if (budget < 0 && !result.isEmpty()) {
                break;
            }
            result.add(line);
        }
        return result;
    }

    private List<String> tokenize(String query) {
        List<String> terms = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return terms;
        }
        String[] parts = query.toLowerCase().split("[\\s,，。；;、:：]+");
        for (String part : parts) {
            if (part.length() >= 2) {
                terms.add(part);
            }
        }
        return terms;
    }

    /**
     * tags 列是 JSON 类型，必须以合法 JSON 数组字符串写入（如 ["auto"] / ["a","b"]）。
     */
    private String toJsonTags(List<String> tags) {
        if (tags == null) {
            return "[]";
        }
        return tags.stream()
                .map(t -> "\"" + t.replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
