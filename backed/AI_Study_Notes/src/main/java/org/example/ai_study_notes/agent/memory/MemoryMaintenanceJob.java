package org.example.ai_study_notes.agent.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.config.AgentProperties;
import org.example.ai_study_notes.agent.memory.episode.MemoryEpisode;
import org.example.ai_study_notes.agent.memory.episode.MemoryEpisodeMapper;
import org.example.ai_study_notes.agent.memory.experience.MemoryExperience;
import org.example.ai_study_notes.agent.memory.experience.MemoryExperienceMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 记忆维护：情景到期归档、未确认经验候选过期清理。
 */
@Slf4j
@Component
public class MemoryMaintenanceJob {

    private final AgentProperties properties;
    private final MemoryEpisodeMapper episodeMapper;
    private final MemoryExperienceMapper experienceMapper;

    public MemoryMaintenanceJob(AgentProperties properties,
                                MemoryEpisodeMapper episodeMapper,
                                MemoryExperienceMapper experienceMapper) {
        this.properties = properties;
        this.episodeMapper = episodeMapper;
        this.experienceMapper = experienceMapper;
    }

    @Scheduled(fixedDelayString = "${agent.maintenance.interval-ms:3600000}")
    public void maintain() {
        if (!properties.getMaintenance().isEnabled()) {
            return;
        }
        try {
            int archived = archiveEpisodes();
            int expired = expireCandidates();
            log.info("记忆维护完成：归档情景 {} 条，清理候选 {} 条", archived, expired);
        } catch (Exception e) {
            log.warn("记忆维护失败: {}", e.getMessage());
        }
    }

    private int archiveEpisodes() {
        LocalDateTime cutoff = LocalDateTime.now()
                .minusDays(properties.getMaintenance().getEpisodeRetentionDays());
        var episodes = episodeMapper.selectList(new LambdaQueryWrapper<MemoryEpisode>()
                .isNull(MemoryEpisode::getArchivedAt)
                .lt(MemoryEpisode::getCreatedAt, cutoff));
        int count = 0;
        for (MemoryEpisode episode : episodes) {
            MemoryEpisode update = new MemoryEpisode();
            update.setId(episode.getId());
            update.setArchivedAt(LocalDateTime.now());
            episodeMapper.updateById(update);
            count++;
        }
        return count;
    }

    private int expireCandidates() {
        LocalDateTime cutoff = LocalDateTime.now()
                .minusDays(properties.getMaintenance().getCandidateRetentionDays());
        var candidates = experienceMapper.selectList(new LambdaQueryWrapper<MemoryExperience>()
                .eq(MemoryExperience::getConfirmed, 0)
                .lt(MemoryExperience::getCreatedAt, cutoff));
        int count = candidates.size();
        for (MemoryExperience candidate : candidates) {
            experienceMapper.deleteById(candidate.getId());
        }
        return count;
    }
}
