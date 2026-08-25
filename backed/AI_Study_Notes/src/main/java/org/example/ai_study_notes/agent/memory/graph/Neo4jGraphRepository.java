package org.example.ai_study_notes.agent.memory.graph;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

import java.util.List;
import java.util.Map;

/**
 * Neo4j 图仓储：实体/关系 upsert 与 1-2 跳展开。只封装 Cypher，业务编排在 Extractor/Retriever。
 */
public class Neo4jGraphRepository {

    public record RelationHit(String subjectEntityId, String subjectName,
                              String objectEntityId, String objectName,
                              RelationType predicate, String context) {
    }

    private final Driver driver;

    public Neo4jGraphRepository(Driver driver) {
        this.driver = driver;
    }

    public static Driver createDriver(String uri, String user, String password) {
        return GraphDatabase.driver(uri, AuthTokens.basic(user, password));
    }

    public String upsertEntity(String name, String type, Long workspaceId, List<String> aliases) {
        String entityId = java.util.UUID.randomUUID().toString();
        try (var session = driver.session()) {
            session.run("""
                    MERGE (e:Entity {entityId: $entityId, workspaceId: $workspaceId})
                    SET e.name = $name, e.type = $type, e.aliases = $aliases,
                        e.scope = 'TEAM', e.createdAt = datetime()
                    """, Map.of(
                    "entityId", entityId,
                    "workspaceId", workspaceId,
                    "name", name,
                    "type", type,
                    "aliases", aliases));
        }
        return entityId;
    }

    public void upsertRelation(String subjectId, RelationType predicate, String objectId,
                               String context, String source, Long workspaceId) {
        try (var session = driver.session()) {
            session.run("""
                    MATCH (a:Entity {entityId: $subjectId, workspaceId: $workspaceId})
                    MATCH (b:Entity {entityId: $objectId, workspaceId: $workspaceId})
                    MERGE (a)-[r:%s]->(b)
                    SET r.context = $context, r.source = $source, r.confirmed = false,
                        r.weight = coalesce(r.weight, 1.0), r.validTo = null,
                        r.workspaceId = $workspaceId, r.createdAt = datetime()
                    """.formatted(predicate.name()), Map.of(
                    "subjectId", subjectId,
                    "objectId", objectId,
                    "workspaceId", workspaceId,
                    "context", context,
                    "source", source));
        }
    }

    public void confirmRelation(String subjectId, RelationType predicate, String objectId,
                                Long workspaceId) {
        try (var session = driver.session()) {
            session.run("""
                    MATCH (a:Entity {entityId: $subjectId, workspaceId: $workspaceId})
                          -[r:%s]->(b:Entity {entityId: $objectId, workspaceId: $workspaceId})
                    SET r.confirmed = true
                    """.formatted(predicate.name()), Map.of(
                    "subjectId", subjectId,
                    "objectId", objectId,
                    "workspaceId", workspaceId));
        }
    }

    public String resolveEntity(String name, Long workspaceId) {
        try (var session = driver.session()) {
            var result = session.run("""
                    MATCH (e:Entity {workspaceId: $workspaceId})
                    WHERE e.name = $name OR any(a IN e.aliases WHERE a = $name)
                    RETURN e.entityId AS entityId LIMIT 1
                    """, Map.of("name", name, "workspaceId", workspaceId));
            return result.hasNext() ? result.next().get("entityId").asString() : null;
        }
    }

    public List<String> findEntityIdsByQuery(String query, Long workspaceId) {
        try (var session = driver.session()) {
            var result = session.run("""
                    MATCH (e:Entity {workspaceId: $workspaceId})
                    WHERE $query CONTAINS e.name
                       OR any(a IN e.aliases WHERE $query CONTAINS a)
                    RETURN e.entityId AS entityId LIMIT 5
                    """, Map.of("query", query, "workspaceId", workspaceId));
            return result.list(record -> record.get("entityId").asString());
        }
    }

    public List<RelationHit> expand(String entityId, Long workspaceId, int depth) {
        List<RelationHit> hits = expandOnce(entityId, workspaceId);
        if (depth >= 2) {
            int base = hits.size();
            for (int i = 0; i < base; i++) {
                hits.addAll(expandOnce(hits.get(i).objectEntityId(), workspaceId));
            }
        }
        return hits.stream().distinct().toList();
    }

    private List<RelationHit> expandOnce(String entityId, Long workspaceId) {
        try (var session = driver.session()) {
            var result = session.run("""
                    MATCH (a:Entity {entityId: $entityId, workspaceId: $workspaceId})
                          -[r]->(b:Entity {workspaceId: $workspaceId})
                    WHERE r.confirmed = true AND r.validTo IS NULL
                    RETURN a.name AS subjectName, b.entityId AS objectId, b.name AS objectName,
                           type(r) AS predicate, r.context AS context
                    LIMIT 20
                    """, Map.of("entityId", entityId, "workspaceId", workspaceId));
            return result.list(record -> new RelationHit(
                    entityId,
                    record.get("subjectName").asString(),
                    record.get("objectId").asString(),
                    record.get("objectName").asString(),
                    RelationType.valueOf(record.get("predicate").asString()),
                    record.get("context").isNull() ? "" : record.get("context").asString()));
        }
    }
}
