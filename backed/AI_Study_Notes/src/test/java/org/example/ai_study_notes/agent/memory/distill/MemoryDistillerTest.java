package org.example.ai_study_notes.agent.memory.distill;

import org.example.ai_study_notes.agent.config.AgentProperties;
import org.example.ai_study_notes.agent.core.AgentAiClient;
import org.example.ai_study_notes.agent.memory.ExperienceMemoryService;
import org.example.ai_study_notes.agent.memory.FactMemoryService;
import org.example.ai_study_notes.agent.knowledge.KnowledgeService;
import org.example.ai_study_notes.agent.memory.episode.EpisodeRecorder;
import org.example.ai_study_notes.agent.memory.vector.MemoryIndexer;
import org.example.ai_study_notes.agent.session.AgentMessage;
import org.example.ai_study_notes.agent.session.MessageService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryDistillerTest {

    @Test
    void extractsFactAndExperienceCandidate() {
        AgentAiClient aiClient = mock(AgentAiClient.class);
        when(aiClient.chat(anyString(), anyString()))
                .thenReturn("{\"facts\":[{\"entity_id\":\"shoe\",\"attribute\":\"price\",\"value\":\"500\",\"confidence\":0.9}],"
                        + "\"experiences\":[{\"task_type\":\"test_case_extraction\",\"rule\":\"提取测试点时需考虑兼容性测试点\",\"confidence\":0.8}]}");
        FactMemoryService factService = mock(FactMemoryService.class);
        ExperienceMemoryService experienceService = mock(ExperienceMemoryService.class);
        MemoryIndexer indexer = mock(MemoryIndexer.class);
        EpisodeRecorder recorder = mock(EpisodeRecorder.class);
        MessageService messageService = mock(MessageService.class);
        when(messageService.list(any())).thenReturn(List.of(
                AgentMessage.builder().role("user").type("text")
                        .content("鞋子多少钱，提取测试点时要考虑兼容性").build()));
        AgentProperties properties = new AgentProperties();
        properties.getDistill().setMinCharacters(5);
        KnowledgeService knowledgeService = mock(KnowledgeService.class);

        MemoryDistiller distiller = new MemoryDistiller(aiClient, factService, experienceService,
                indexer, recorder, messageService, properties, knowledgeService);
        distiller.extractIfNeeded(1L, 1L);

        verify(factService).upsert(any(), any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyDouble());
        verify(experienceService).saveCandidate(any(), any(), anyString(), anyString(), anyString(), anyDouble());
    }

    @Test
    void extractsPreferenceAndKnowledge() {
        AgentAiClient aiClient = mock(AgentAiClient.class);
        when(aiClient.chat(anyString(), anyString()))
                .thenReturn("{\"facts\":[],\"experiences\":[],"
                        + "\"preferences\":[{\"key\":\"default_env\",\"content\":\"默认环境是 staging\"}],"
                        + "\"knowledge\":[{\"title\":\"登录接口超时\",\"category\":\"经验教训\",\"content\":\"登录接口在并发下易超时\"}]}");
        FactMemoryService factService = mock(FactMemoryService.class);
        ExperienceMemoryService experienceService = mock(ExperienceMemoryService.class);
        MemoryIndexer indexer = mock(MemoryIndexer.class);
        EpisodeRecorder recorder = mock(EpisodeRecorder.class);
        MessageService messageService = mock(MessageService.class);
        when(messageService.list(any())).thenReturn(List.of(
                AgentMessage.builder().role("user").type("text")
                        .content("默认环境用 staging，登录接口在并发下容易超时").build()));
        AgentProperties properties = new AgentProperties();
        properties.getDistill().setMinCharacters(5);
        KnowledgeService knowledgeService = mock(KnowledgeService.class);

        MemoryDistiller distiller = new MemoryDistiller(aiClient, factService, experienceService,
                indexer, recorder, messageService, properties, knowledgeService);
        distiller.extractIfNeeded(1L, 1L);

        verify(factService).upsert(eq(1L), eq(1L), eq("user"), eq("default_env"),
                eq("默认环境是 staging"), anyString(), anyString(), anyDouble());
        verify(knowledgeService).saveCandidate(eq(1L), eq("登录接口超时"), eq("登录接口在并发下易超时"), any(), any());
    }
}
