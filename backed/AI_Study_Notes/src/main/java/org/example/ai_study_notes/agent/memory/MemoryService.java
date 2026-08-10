package org.example.ai_study_notes.agent.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
            existing.setTags(tags == null ? null : String.join(",", tags));
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
                .tags(tags == null ? null : String.join(",", tags))
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
     * 供上下文注入：返回最近确认的 Top-N 条记忆。
     */
    public List<String> injectable(Long userId) {
        List<MemoryEntry> entries = memoryMapper.selectList(new LambdaQueryWrapper<MemoryEntry>()
                .eq(MemoryEntry::getUserId, userId)
                .eq(MemoryEntry::getConfirmed, 1)
                .orderByDesc(MemoryEntry::getUpdatedAt)
                .last("limit " + MAX_INJECT));
        List<String> result = new ArrayList<>();
        for (MemoryEntry entry : entries) {
            result.add(entry.getMemKey() + ": " + entry.getContentMd());
        }
        return result;
    }
}
