package org.example.ai_study_notes.agent.memory.vector;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points.Condition;
import io.qdrant.client.grpc.Points.FieldCondition;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.Match;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.Vector;
import io.qdrant.client.grpc.Points.Vectors;
import io.qdrant.client.grpc.Points.WithPayloadSelector;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.config.AgentProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Qdrant 向量仓储：集合初始化、upsert、带过滤的余弦检索。
 * 外部依赖封装类，业务代码不感知 gRPC/protobuf 细节。
 */
@Slf4j
@Component
public class QdrantVectorStore {

    public static final String COLLECTION_FACTS = "facts";
    public static final String COLLECTION_EXPERIENCES = "experiences";
    public static final String COLLECTION_KNOWLEDGE = "knowledge";

    public record Hit(String id, double score, Map<String, String> payload) {
    }

    private final QdrantClient client;
    private final AgentProperties properties;

    public QdrantVectorStore(AgentProperties properties) {
        this.properties = properties;
        AgentProperties.Qdrant cfg = properties.getQdrant();
        this.client = new QdrantClient(
                QdrantGrpcClient.newBuilder(cfg.getHost(), cfg.getPort(), false).build());
        initCollections();
    }

    private void initCollections() {
        int dim = properties.getEmbedding().getDimensions();
        for (String name : List.of(COLLECTION_FACTS, COLLECTION_EXPERIENCES, COLLECTION_KNOWLEDGE)) {
            try {
                String collection = collectionName(name);
                boolean exists = client.collectionExistsAsync(collection).get(5, TimeUnit.SECONDS);
                if (!exists) {
                    client.createCollectionAsync(collection, VectorParams.newBuilder()
                                    .setSize(dim).setDistance(Distance.Cosine).build())
                            .get(30, TimeUnit.SECONDS);
                    log.info("Qdrant 集合已创建: {}", collection);
                }
            } catch (Exception e) {
                log.warn("Qdrant 初始化集合失败 {}: {}", name, e.getMessage());
            }
        }
    }

    public String collectionName(String name) {
        String prefix = properties.getQdrant().getCollectionPrefix();
        return prefix == null || prefix.isBlank() ? name : prefix + name;
    }

    public void upsert(String collection, String pointId, List<Float> vector, Map<String, Object> payload) {
        try {
            Map<String, Object> fullPayload = new LinkedHashMap<>(payload);
            fullPayload.put("id", pointId);
            PointStruct point = PointStruct.newBuilder()
                    .setId(PointIdFactory.id(UUID.nameUUIDFromBytes(pointId.getBytes(StandardCharsets.UTF_8))))
                    .setVectors(Vectors.newBuilder()
                            .setVector(Vector.newBuilder().addAllData(vector).build()))
                    .putAllPayload(toPayload(fullPayload))
                    .build();
            client.upsertAsync(collection, List.of(point)).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Qdrant upsert 失败: " + e.getMessage(), e);
        }
    }

    public List<Hit> search(String collection, List<Float> vector, String workspaceId,
                            Boolean confirmedOnly, int limit) {
        try {
            SearchPoints.Builder builder = SearchPoints.newBuilder()
                    .setCollectionName(collection)
                    .addAllVector(vector)
                    .setLimit(limit)
                    .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true));
            Filter.Builder filter = Filter.newBuilder();
            if (workspaceId != null) {
                filter.addMust(Condition.newBuilder().setField(FieldCondition.newBuilder()
                        .setKey("workspace_id")
                        .setMatch(Match.newBuilder().setKeyword(workspaceId))));
            }
            if (confirmedOnly != null && confirmedOnly) {
                filter.addMust(Condition.newBuilder().setField(FieldCondition.newBuilder()
                        .setKey("confirmed")
                        .setMatch(Match.newBuilder().setInteger(1L))));
            }
            if (workspaceId != null || confirmedOnly != null) {
                builder.setFilter(filter);
            }
            return client.searchAsync(builder.build()).get(30, TimeUnit.SECONDS).stream()
                    .map(p -> new Hit(
                            payloadId(p),
                            p.getScore(),
                            payloadToStringMap(p.getPayloadMap())))
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Qdrant search 失败: " + e.getMessage(), e);
        }
    }

    private Map<String, JsonWithInt.Value> toPayload(Map<String, Object> payload) {
        Map<String, JsonWithInt.Value> out = new LinkedHashMap<>();
        payload.forEach((k, v) -> {
            if (v instanceof String s) {
                out.put(k, ValueFactory.value(s));
            } else if (v instanceof Integer || v instanceof Long) {
                out.put(k, ValueFactory.value(((Number) v).longValue()));
            } else if (v instanceof Number n) {
                out.put(k, ValueFactory.value(n.doubleValue()));
            } else if (v instanceof Boolean b) {
                out.put(k, ValueFactory.value(b));
            }
        });
        return out;
    }

    private String payloadId(ScoredPoint point) {
        JsonWithInt.Value id = point.getPayloadMap().get("id");
        if (id != null && id.hasStringValue()) {
            return id.getStringValue();
        }
        return String.valueOf(point.getId());
    }

    private Map<String, String> payloadToStringMap(Map<String, JsonWithInt.Value> payload) {
        Map<String, String> out = new LinkedHashMap<>();
        payload.forEach((k, v) -> {
            if (v.hasStringValue()) {
                out.put(k, v.getStringValue());
            } else if (v.hasIntegerValue()) {
                out.put(k, String.valueOf(v.getIntegerValue()));
            } else if (v.hasDoubleValue()) {
                out.put(k, String.valueOf(v.getDoubleValue()));
            } else if (v.hasBoolValue()) {
                out.put(k, String.valueOf(v.getBoolValue()));
            }
        });
        return out;
    }
}
