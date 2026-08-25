package org.example.ai_study_notes.agent.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.memory.fact.MemoryFact;
import org.example.ai_study_notes.agent.memory.fact.MemoryFactMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 事实记忆服务：按 (workspace, entity, attribute) 维护当前值，变更时版本归档（valid_to）。
 */
@Slf4j
@Service
public class FactMemoryService {

    public static final LocalDateTime OPEN_END = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    private final MemoryFactMapper mapper;

    public FactMemoryService(MemoryFactMapper mapper) {
        this.mapper = mapper;
    }

    public MemoryFact upsert(Long workspaceId, Long userId, String entityId, String attribute,
                             String value, String sourceType, String sourceRef, double confidence) {
        MemoryFact current = findCurrent(workspaceId, entityId, attribute);
        if (current != null && value.equals(current.getFactValue())) {
            return current;
        }
        if (current != null) {
            MemoryFact archive = new MemoryFact();
            archive.setId(current.getId());
            archive.setValidTo(LocalDateTime.now());
            mapper.updateById(archive);
        }
        LocalDateTime now = LocalDateTime.now();
        MemoryFact next = MemoryFact.builder()
                .workspaceId(workspaceId).userId(userId)
                .entityId(entityId).attribute(attribute)
                .factValue(value)
                .validFrom(now).validTo(OPEN_END)
                .version(current == null ? 1 : current.getVersion() + 1)
                .sourceType(sourceType).sourceRef(sourceRef)
                .confidence(BigDecimal.valueOf(confidence))
                .createdAt(now).updatedAt(now)
                .build();
        mapper.insert(next);
        return next;
    }

    public MemoryFact findCurrent(Long workspaceId, String entityId, String attribute) {
        return mapper.selectOne(new LambdaQueryWrapper<MemoryFact>()
                .eq(MemoryFact::getWorkspaceId, workspaceId)
                .eq(MemoryFact::getEntityId, entityId)
                .eq(MemoryFact::getAttribute, attribute)
                .eq(MemoryFact::getValidTo, OPEN_END));
    }

    public List<MemoryFact> searchKeyword(Long workspaceId, String query) {
        return mapper.selectList(new LambdaQueryWrapper<MemoryFact>()
                .eq(MemoryFact::getWorkspaceId, workspaceId)
                .and(w -> w.like(MemoryFact::getAttribute, query)
                        .or().like(MemoryFact::getFactValue, query)));
    }
}
