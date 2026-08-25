package org.example.ai_study_notes.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 模块配置，对应 application yml 中 agent.* 配置项。
 */
@Data
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private DeepSeek deepseek = new DeepSeek();
    private Jwt jwt = new Jwt();
    private Loop loop = new Loop();
    private Auth auth = new Auth();
    private String dataDir = "./agent-data";
    private String skillsDir = "";
    private Embedding embedding = new Embedding();
    private Qdrant qdrant = new Qdrant();
    private Distill distill = new Distill();
    private Neo4j neo4j = new Neo4j();
    private Maintenance maintenance = new Maintenance();
    private Confirm confirm = new Confirm();

    @Data
    public static class DeepSeek {
        private String baseUrl = "https://api.deepseek.com";
        private String apiKey = "";
        private String model = "deepseek-v4-flash";
        private double temperature = 0.3;
        private int maxTokens = 4096;
        private int generationMaxTokens = 8192;
        private int timeoutSeconds = 300;
    }

    @Data
    public static class Jwt {
        private String secret = "";
        private long expireMinutes = 1440;
    }

    @Data
    public static class Loop {
        private int maxTurns = 20;
        private long timeoutSeconds = 300;
        private int maxContextTokens = 0;
    }

    @Data
    public static class Auth {
        private String adminPassword = "12345678";
    }

    @Data
    public static class Embedding {
        private String baseUrl = "http://127.0.0.1:11434/v1";
        private String apiKey = "";
        private String model = "bge-m3";
        private int dimensions = 1024;
        private int timeoutSeconds = 60;
    }

    @Data
    public static class Qdrant {
        private String host = "127.0.0.1";
        private int port = 6334;
        private String apiKey = "";
        private String collectionPrefix = "";
    }

    @Data
    public static class Distill {
        private boolean enabled = true;
        private int minCharacters = 60;
    }

    @Data
    public static class Neo4j {
        private String uri = "bolt://127.0.0.1:7687";
        private String user = "neo4j";
        private String password = "autotest123456";
    }

    @Data
    public static class Maintenance {
        private boolean enabled = true;
        private long intervalMs = 3_600_000;
        private int episodeRetentionDays = 90;
        private int candidateRetentionDays = 7;
    }

    @Data
    public static class Confirm {
        private boolean enabled = true;
        private int windowHours = 24;
        private int askLimit = 5;
    }
}
