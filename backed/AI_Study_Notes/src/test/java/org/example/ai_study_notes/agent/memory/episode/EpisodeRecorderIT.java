package org.example.ai_study_notes.agent.memory.episode;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Tag("integration")
@Transactional
class EpisodeRecorderIT {

    @Autowired
    private EpisodeRecorder recorder;

    @Test
    void recordInsertsEpisode() {
        MemoryEpisode episode = recorder.record(1L, 1L, "conversation", "msg:123",
                "[user] 鞋子多少钱\n[assistant] 500 元");
        assertEquals("conversation", episode.getSourceType());
        assertEquals("msg:123", episode.getSourceRef());
    }
}
