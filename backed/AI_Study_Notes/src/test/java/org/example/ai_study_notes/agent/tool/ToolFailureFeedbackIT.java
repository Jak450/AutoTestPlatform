package org.example.ai_study_notes.agent.tool;

import org.example.ai_study_notes.agent.memory.episode.MemoryEpisodeMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Tag("integration")
@Transactional
class ToolFailureFeedbackIT {

    @Autowired
    private ToolExecutionService executionService;
    @Autowired
    private MemoryEpisodeMapper episodeMapper;

    @Test
    void failingToolRecordsEpisode() {
        long before = episodeMapper.selectCount(null);
        executionService.execute("unknown_tool_xyz",
                Map.of(), ToolContext.builder().userId(1L).conversationId(1L).build(), false);
        long after = episodeMapper.selectCount(null);
        assertTrue(after > before);
    }
}
