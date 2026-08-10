package org.example.ai_study_notes.agent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai_study_notes.agent.core.AgentAiClient;
import org.example.ai_study_notes.agent.knowledge.KnowledgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 自动提炼：偏好直接入库（confirmed），知识走智能保存（去重/追加/新建）；同名偏好跳过。
 */
class MemoryExtractorTest {

    private AgentAiClient aiClient;
    private MemoryService memoryService;
    private KnowledgeService knowledgeService;
    private MemoryExtractor extractor;

    @BeforeEach
    void setUp() {
        aiClient = mock(AgentAiClient.class);
        memoryService = mock(MemoryService.class);
        knowledgeService = mock(KnowledgeService.class);
        extractor = new MemoryExtractor(aiClient, memoryService, knowledgeService, new ObjectMapper());
    }

    @Test
    void preferenceIsAutoSavedConfirmed() {
        when(aiClient.chat(anyString(), anyString())).thenReturn(
                "[{\"type\":\"preference\",\"key\":\"default_env\",\"content\":\"默认环境是测试环境\"}]");
        when(memoryService.save(anyLong(), anyString(), anyString(), anyList(), anyBoolean(), anyLong()))
                .thenReturn(MemoryEntry.builder().memKey("default_env").build());

        extractor.extractIfNeeded(1L, 1L, "x".repeat(250));

        verify(memoryService).save(eq(1L), eq("default_env"), eq("默认环境是测试环境"),
                eq(List.of("auto")), eq(false), eq(1L));
        verify(knowledgeService, never()).saveSmart(any(), any(), any(), any(), any());
    }

    @Test
    void duplicatePreferenceIsSkippedSilently() {
        when(aiClient.chat(anyString(), anyString())).thenReturn(
                "[{\"type\":\"preference\",\"key\":\"default_env\",\"content\":\"默认环境\"}]");
        when(memoryService.save(anyLong(), anyString(), anyString(), anyList(), anyBoolean(), anyLong()))
                .thenThrow(new IllegalArgumentException("记忆已存在"));

        extractor.extractIfNeeded(1L, 1L, "x".repeat(250));

        verify(memoryService).save(anyLong(), anyString(), anyString(), anyList(), anyBoolean(), anyLong());
    }

    @Test
    void knowledgeGoesThroughSmartSave() {
        when(aiClient.chat(anyString(), anyString())).thenReturn(
                "[{\"type\":\"knowledge\",\"title\":\"经验A\",\"content\":\"内容A\",\"category\":\"经验教训\",\"action\":\"create\"}]");
        when(knowledgeService.saveSmart(anyLong(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn(new KnowledgeService.SmartSaveResult("created", null, null));

        extractor.extractIfNeeded(1L, 1L, "x".repeat(250));

        verify(knowledgeService).saveSmart(eq(1L), eq("经验A"), eq("内容A"), eq("经验教训"), eq(List.of("auto")));
        verify(memoryService, never()).save(anyLong(), anyString(), anyString(), anyList(), anyBoolean(), anyLong());
    }

    @Test
    void preferenceMergeGoesToExistingKey() {
        when(aiClient.chat(anyString(), anyString())).thenReturn(
                "[{\"type\":\"preference\",\"key\":\"timeout_rule\",\"content\":\"接口超时统一 60 秒\","
                        + "\"action\":\"merge\",\"targetKey\":\"default_timeout\"}]");
        when(memoryService.merge(eq(1L), eq("default_timeout"), eq("接口超时统一 60 秒")))
                .thenReturn(MemoryEntry.builder().memKey("default_timeout").build());

        extractor.extractIfNeeded(1L, 1L, "x".repeat(250));

        verify(memoryService).merge(eq(1L), eq("default_timeout"), eq("接口超时统一 60 秒"));
        verify(memoryService, never()).save(anyLong(), anyString(), anyString(), anyList(), anyBoolean(), anyLong());
    }

    @Test
    void preferenceSkipDoesNotSave() {
        when(aiClient.chat(anyString(), anyString())).thenReturn(
                "[{\"type\":\"preference\",\"key\":\"auth_header\",\"content\":\"所有接口统一 token 鉴权\","
                        + "\"action\":\"skip\"}]");

        extractor.extractIfNeeded(1L, 1L, "x".repeat(250));

        verify(memoryService, never()).save(anyLong(), anyString(), anyString(), anyList(), anyBoolean(), anyLong());
        verify(memoryService, never()).merge(anyLong(), anyString(), anyString());
    }
}
