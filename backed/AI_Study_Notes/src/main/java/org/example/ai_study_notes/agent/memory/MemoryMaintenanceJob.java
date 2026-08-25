package org.example.ai_study_notes.agent.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.config.AgentProperties;
import org.example.ai_study_notes.agent.memory.episode.MemoryEpisode;
import org.example.ai_study_notes.agent.memory.episode.MemoryEpisodeMapper;
import org.example.ai_study_notes.agent.memory.experience.MemoryExperience;
import org.example.ai_study_notes.agent.memory.experience.MemoryExperienceMapper;
import org.example.ai_study_notes.agent.memory.fact.MemoryFact;
import org.example.ai_study_notes.agent.memory.fact.MemoryFactMapper;
import org.example.ai_study_notes.agent.memory.graph.Neo4jGraphRepository;
import org.example.ai_study_notes.agent.memory.vector.MemoryIndexer;
import org.example.ai_study_notes.agent.memory.vector.QdrantVectorStore;
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
    private final MemoryFactMapper factMapper;
    private final QdrantVectorStore vectorStore;
    private final Neo4jGraphRepository graphRepository;
    private final ExperienceMemoryService experienceService;
    private final MemoryIndexer indexer;

    public MemoryMaintenanceJob(AgentProperties properties,
                                MemoryEpisodeMapper episodeMapper,
                                MemoryExperienceMapper experienceMapper,
                                MemoryFactMapper factMapper,
                                QdrantVectorStore vectorStore,
                                Neo4jGraphRepository graphRepository,
                                ExperienceMemoryService experienceService,
                                MemoryIndexer indexer) {
        this.properties = properties;
        this.episodeMapper = episodeMapper;
        this.experienceMapper = experienceMapper;
        this.factMapper = factMapper;
        this.vectorStore = vectorStore;
        this.graphRepository = graphRepository;
        this.experienceService = experienceService;
        this.indexer = indexer;
    }

    @Scheduled(fixedDelayString = "${agent.maintenance.interval-ms:3600000}")
    public void maintain() {
        if (!properties.getMaintenance().isEnabled()) {
            return;
        }
        try {
            int archived = archiveEpisodes();
            int expired = expireCandidates();
            int factVectors = archiveArchivedFactVectors();
            graphRepository.decayUnconfirmedRelations(30, 0.9, 0.2);
            graphRepository.confirmUnconfirmedRelationsOlderThan(
                    properties.getConfirm().getWindowHours());
            int autoConfirmed = autoConfirmExpiredExperiences();
            log.info("记忆维护完成：归档情景 {} 条，清理候选 {} 条，清理归档事实向量 {} 条，超时自动确认经验 {} 条",
                    archived, expired, factVectors, autoConfirmed);
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
        for (MemoryExperience candidate : candidates) {
            experienceMapper.deleteById(candidate.getId());
        }
        vectorStore.deleteByIds(vectorStore.collectionName(QdrantVectorStore.COLLECTION_EXPERIENCES),
                candidates.stream().map(c -> "experience:" + c.getId()).toList());
        return candidates.size();
    }

    private int archiveArchivedFactVectors() {
        var archived = factMapper.selectList(new LambdaQueryWrapper<MemoryFact>()
                .ne(MemoryFact::getValidTo, FactMemoryService.OPEN_END));
        vectorStore.deleteByIds(vectorStore.collectionName(QdrantVectorStore.COLLECTION_FACTS),
                archived.stream().map(f -> "fact:" + f.getId()).toList());
        return archived.size();
    }

    private int autoConfirmExpiredExperiences() {
        LocalDateTime cutoff = LocalDateTime.now()
                .minusHours(properties.getConfirm().getWindowHours());
        var candidates = experienceMapper.selectList(new LambdaQueryWrapper<MemoryExperience>()
                .eq(MemoryExperience::getConfirmed, 0)
                .lt(MemoryExperience::getCreatedAt, cutoff));
        for (MemoryExperience candidate : candidates) {
            experienceService.confirm(candidate.getId());
            candidate.setConfirmed(1);
            indexer.indexExperience(candidate);
        }
        return candidates.size();
    }
}
