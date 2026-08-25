package org.example.ai_study_notes.agent.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.ai_study_notes.agent.memory.experience.MemoryExperience;
import org.example.ai_study_notes.agent.memory.experience.MemoryExperienceMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 经验记忆服务：候选保存（confirmed=0）+ 确认（confirmed=1）+ 关键词检索。
 */
@Service
public class ExperienceMemoryService {

    private final MemoryExperienceMapper mapper;

    public ExperienceMemoryService(MemoryExperienceMapper mapper) {
        this.mapper = mapper;
    }

    public MemoryExperience saveCandidate(Long workspaceId, Long userId, String taskType,
                                          String ruleText, String evidence, double confidence) {
        MemoryExperience exp = MemoryExperience.builder()
                .workspaceId(workspaceId).userId(userId)
                .taskType(taskType).ruleText(ruleText).evidence(evidence)
                .hits(0).confidence(BigDecimal.valueOf(confidence))
                .confirmed(0)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        mapper.insert(exp);
        return exp;
    }

    public List<MemoryExperience> listConfirmed(Long workspaceId, String taskType) {
        return mapper.selectList(new LambdaQueryWrapper<MemoryExperience>()
                .eq(MemoryExperience::getWorkspaceId, workspaceId)
                .eq(MemoryExperience::getConfirmed, 1)
                .eq(taskType != null, MemoryExperience::getTaskType, taskType));
    }

    public List<MemoryExperience> searchConfirmedKeyword(Long workspaceId, String query) {
        return mapper.selectList(new LambdaQueryWrapper<MemoryExperience>()
                .eq(MemoryExperience::getWorkspaceId, workspaceId)
                .eq(MemoryExperience::getConfirmed, 1)
                .like(MemoryExperience::getRuleText, query));
    }

    public void confirm(Long id) {
        MemoryExperience update = new MemoryExperience();
        update.setId(id);
        update.setConfirmed(1);
        update.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(update);
    }
}
