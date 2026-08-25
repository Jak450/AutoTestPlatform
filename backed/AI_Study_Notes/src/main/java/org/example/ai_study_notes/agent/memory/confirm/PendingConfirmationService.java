package org.example.ai_study_notes.agent.memory.confirm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.config.AgentProperties;
import org.example.ai_study_notes.agent.memory.ExperienceMemoryService;
import org.example.ai_study_notes.agent.memory.experience.MemoryExperience;
import org.example.ai_study_notes.agent.memory.experience.MemoryExperienceMapper;
import org.example.ai_study_notes.agent.memory.graph.Neo4jGraphRepository;
import org.example.ai_study_notes.agent.memory.vector.MemoryIndexer;
import org.example.ai_study_notes.agent.memory.vector.QdrantVectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 待确认记忆服务：列出候选、确认/纠正/拒绝（经验优先，其次关系）。
 */
@Slf4j
@Service
public class PendingConfirmationService {

    private final MemoryExperienceMapper experienceMapper;
    private final ExperienceMemoryService experienceService;
    private final Neo4jGraphRepository graphRepository;
    private final MemoryIndexer indexer;
    private final QdrantVectorStore vectorStore;
    private final AgentProperties properties;

    public PendingConfirmationService(MemoryExperienceMapper experienceMapper,
                                      ExperienceMemoryService experienceService,
                                      Neo4jGraphRepository graphRepository,
                                      MemoryIndexer indexer,
                                      QdrantVectorStore vectorStore,
                                      AgentProperties properties) {
        this.experienceMapper = experienceMapper;
        this.experienceService = experienceService;
        this.graphRepository = graphRepository;
        this.indexer = indexer;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public String pendingPrompt(Long userId, int limit) {
        List<String> lines = new ArrayList<>();
        int n = 0;
        var experiences = experienceMapper.selectList(new LambdaQueryWrapper<MemoryExperience>()
                .eq(MemoryExperience::getUserId, userId)
                .eq(MemoryExperience::getConfirmed, 0)
                .orderByAsc(MemoryExperience::getCreatedAt)
                .last("limit " + Math.max(1, limit)));
        for (MemoryExperience exp : experiences) {
            lines.add(++n + ") 经验【" + exp.getTaskType() + "】" + exp.getRuleText());
        }
        for (Neo4jGraphRepository.RelationHit hit :
                graphRepository.listUnconfirmedRelations(userId, Math.max(1, limit - n))) {
            lines.add(++n + ") 关系【" + hit.subjectName() + " " + hit.predicate() + " "
                    + hit.objectName() + "】" + hit.context());
        }
        if (lines.isEmpty()) {
            return "";
        }
        return "用户已完成当前工作。请在回复末尾逐条向用户确认以下待确认记忆（用户可回答“对 / 不对，改成X”）：\n"
                + String.join("\n", lines);
    }

    public boolean confirmNextExperience(Long userId) {
        MemoryExperience exp = firstPending(userId);
        if (exp != null) {
            experienceService.confirm(exp.getId());
            exp.setConfirmed(1);
            indexer.indexExperience(exp);
            return true;
        }
        return false;
    }

    public boolean rejectNextExperience(Long userId) {
        MemoryExperience exp = firstPending(userId);
        if (exp != null) {
            experienceMapper.deleteById(exp.getId());
            vectorStore.deleteByIds(vectorStore.collectionName(QdrantVectorStore.COLLECTION_EXPERIENCES),
                    List.of("experience:" + exp.getId()));
            return true;
        }
        return false;
    }

    public boolean correctNextExperience(Long userId, String correctedRule) {
        MemoryExperience exp = firstPending(userId);
        if (exp != null && correctedRule != null && !correctedRule.isBlank()) {
            MemoryExperience update = new MemoryExperience();
            update.setId(exp.getId());
            update.setRuleText(correctedRule);
            update.setConfirmed(1);
            experienceMapper.updateById(update);
            exp.setRuleText(correctedRule);
            exp.setConfirmed(1);
            indexer.indexExperience(exp);
            return true;
        }
        return false;
    }

    public boolean confirmNextRelation(Long userId) {
        var pending = graphRepository.listUnconfirmedRelations(userId, 1);
        if (!pending.isEmpty()) {
            var hit = pending.get(0);
            graphRepository.confirmRelation(hit.subjectEntityId(), hit.predicate(),
                    hit.objectEntityId(), userId);
            return true;
        }
        return false;
    }

    public boolean rejectNextRelation(Long userId) {
        var pending = graphRepository.listUnconfirmedRelations(userId, 1);
        if (!pending.isEmpty()) {
            var hit = pending.get(0);
            graphRepository.deleteRelation(hit.subjectEntityId(), hit.predicate(),
                    hit.objectEntityId(), userId);
            return true;
        }
        return false;
    }

    public boolean correctNextRelation(Long userId, String correctedText) {
        var pending = graphRepository.listUnconfirmedRelations(userId, 1);
        if (!pending.isEmpty() && correctedText != null && !correctedText.isBlank()) {
            var hit = pending.get(0);
            graphRepository.updateRelationContext(hit.subjectEntityId(), hit.predicate(),
                    hit.objectEntityId(), correctedText, userId);
            return true;
        }
        return false;
    }

    private MemoryExperience firstPending(Long userId) {
        return experienceMapper.selectOne(new LambdaQueryWrapper<MemoryExperience>()
                .eq(MemoryExperience::getUserId, userId)
                .eq(MemoryExperience::getConfirmed, 0)
                .orderByAsc(MemoryExperience::getCreatedAt)
                .last("limit 1"));
    }
}
