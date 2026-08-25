package org.example.ai_study_notes.agent.memory.episode;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * L3 情景记录：把"发生过什么"（对话尾部/工具结果/反馈）写入 memory_episode，作为提炼原料与审计来源。
 */
@Service
public class EpisodeRecorder {

    private final MemoryEpisodeMapper mapper;

    public EpisodeRecorder(MemoryEpisodeMapper mapper) {
        this.mapper = mapper;
    }

    public MemoryEpisode record(Long workspaceId, Long userId, String sourceType,
                                String sourceRef, String content) {
        MemoryEpisode episode = MemoryEpisode.builder()
                .workspaceId(workspaceId).userId(userId)
                .sourceType(sourceType).sourceRef(sourceRef)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();
        mapper.insert(episode);
        return episode;
    }
}
