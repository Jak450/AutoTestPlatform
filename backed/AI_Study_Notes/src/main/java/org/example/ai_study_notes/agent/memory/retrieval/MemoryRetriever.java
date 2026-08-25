package org.example.ai_study_notes.agent.memory.retrieval;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.knowledge.KnowledgeDoc;
import org.example.ai_study_notes.agent.knowledge.KnowledgeService;
import org.example.ai_study_notes.agent.memory.ExperienceMemoryService;
import org.example.ai_study_notes.agent.memory.FactMemoryService;
import org.example.ai_study_notes.agent.memory.embedding.EmbeddingClient;
import org.example.ai_study_notes.agent.memory.graph.GraphRetriever;
import org.example.ai_study_notes.agent.memory.vector.QdrantVectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 记忆检索：查询理解（关键词）→ 多路召回（向量/关键词）→ RRF 融合 → 注入组装。
 * 业务层只依赖 Service 与仓储封装，不直接接触 Mapper。
 */
@Service
@Slf4j
public class MemoryRetriever {

    public record RankedItem(String type, String id, String content, double score) {
    }

    public record MemoryInjection(String facts, String experiences, String knowledge, String relations) {
    }

    private final FactMemoryService factService;
    private final ExperienceMemoryService experienceService;
    private final KnowledgeService knowledgeService;
    private final EmbeddingClient embeddingClient;
    private final QdrantVectorStore vectorStore;
    private final GraphRetriever graphRetriever;

    public MemoryRetriever(FactMemoryService factService,
                           ExperienceMemoryService experienceService,
                           KnowledgeService knowledgeService,
                           EmbeddingClient embeddingClient,
                           QdrantVectorStore vectorStore,
                           GraphRetriever graphRetriever) {
        this.factService = factService;
        this.experienceService = experienceService;
        this.knowledgeService = knowledgeService;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
        this.graphRetriever = graphRetriever;
    }

    public List<RankedItem> retrieve(Long workspaceId, Long userId, String query, int topK) {
        List<List<String>> rankedIds = new ArrayList<>();
        Map<String, RankedItem> byId = new LinkedHashMap<>();
        List<Float> queryVec = null;
        try {
            queryVec = embeddingClient.embed(query);
        } catch (Exception e) {
            log.warn("Embedding 不可用，降级为纯关键词检索: {}", e.getMessage());
        }
        if (queryVec != null) {
            addVectorRoute(rankedIds, byId,
                    vectorStore.collectionName(QdrantVectorStore.COLLECTION_FACTS), queryVec,
                    workspaceId, null, "fact", topK * 2,
                    hit -> "事实: " + hit.payload().getOrDefault("text", ""));
            addVectorRoute(rankedIds, byId,
                    vectorStore.collectionName(QdrantVectorStore.COLLECTION_EXPERIENCES), queryVec,
                    workspaceId, true, "experience", topK * 2,
                    hit -> "经验: " + hit.payload().getOrDefault("text", ""));
        }

        addKeywordRoute(rankedIds, byId, factService.searchKeyword(workspaceId, query),
                f -> "fact:" + f.getId(),
                f -> new RankedItem("fact", String.valueOf(f.getId()),
                        "事实: " + f.getEntityId() + "." + f.getAttribute() + " = " + f.getFactValue(), 0.0));

        addKeywordRoute(rankedIds, byId, experienceService.searchConfirmedKeyword(workspaceId, query),
                e -> "experience:" + e.getId(),
                e -> new RankedItem("experience", String.valueOf(e.getId()),
                        "经验: " + e.getRuleText(), 0.0));

        addKeywordRoute(rankedIds, byId, knowledgeService.search(userId, query, topK * 2),
                d -> "knowledge:" + d.slug(),
                d -> new RankedItem("knowledge", d.slug(),
                        "知识: [" + d.category() + "] " + d.title() + ": " + d.snippet(), 0.0));

        Map<String, Double> scores = RrfFusion.fuse(rankedIds, RrfFusion.DEFAULT_K);
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> byId.get(e.getKey()))
                .toList();
    }

    public MemoryInjection inject(Long workspaceId, Long userId, String query, int budgetChars) {
        List<RankedItem> items = retrieve(workspaceId, userId, query, 20);
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, String> content = new LinkedHashMap<>();
        List<String> factMust = new ArrayList<>();
        for (RankedItem item : items) {
            scores.put(item.type() + ":" + item.id(), item.score());
            content.put(item.type() + ":" + item.id(), item.content());
            if ("fact".equals(item.type())) {
                factMust.add(item.type() + ":" + item.id());
            }
        }
        int third = budgetChars / 3;
        String facts = InjectionAssembler.assemble(scores, content, third, factMust);
        String experiences = InjectionAssembler.assemble(scores, content, third, List.of());
        String knowledge = InjectionAssembler.assemble(scores, content, third, List.of());
        String relations = graphRetriever.expandForQuery(query, workspaceId, third);
        return new MemoryInjection(facts, experiences, knowledge, relations);
    }

    private void addVectorRoute(List<List<String>> rankedIds, Map<String, RankedItem> byId,
                                String collection, List<Float> queryVec, Long workspaceId,
                                Boolean confirmedOnly, String type, int limit,
                                Function<QdrantVectorStore.Hit, String> contentOf) {
        List<QdrantVectorStore.Hit> hits = vectorStore.search(collection, queryVec,
                String.valueOf(workspaceId), confirmedOnly, limit);
        List<String> ids = new ArrayList<>();
        for (QdrantVectorStore.Hit hit : hits) {
            String id = type + ":" + hit.id();
            ids.add(id);
            byId.putIfAbsent(id, new RankedItem(type, hit.id(), contentOf.apply(hit), hit.score()));
        }
        rankedIds.add(ids);
    }

    private <T> void addKeywordRoute(List<List<String>> rankedIds, Map<String, RankedItem> byId,
                                     List<T> sources, Function<T, String> idOf,
                                     Function<T, RankedItem> itemOf) {
        List<String> ids = new ArrayList<>();
        for (T source : sources) {
            String id = idOf.apply(source);
            ids.add(id);
            byId.putIfAbsent(id, itemOf.apply(source));
        }
        rankedIds.add(ids);
    }
}
