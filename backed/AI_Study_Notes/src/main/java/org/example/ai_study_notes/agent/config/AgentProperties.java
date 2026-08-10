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

    @Data
    public static class DeepSeek {
        private String baseUrl = "https://api.deepseek.com";
        private String apiKey = "";
        private String model = "deepseek-v4-flash";
        private double temperature = 0.3;
        private int maxTokens = 4096;
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
}
