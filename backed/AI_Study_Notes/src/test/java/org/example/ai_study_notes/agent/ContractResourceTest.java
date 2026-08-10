package org.example.ai_study_notes.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai_study_notes.agent.contract.AgentContract;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约文件（agent-contracts.json）与代码枚举双向校验（CONTRACT-1）。
 */
class ContractResourceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void contractResourceMatchesCodeEnums() throws Exception {
        JsonNode root = objectMapper.readTree(new ClassPathResource("agent/agent-contracts.json").getInputStream());

        List<String> contractMessages = asStringList(root.get("messageTypes"));
        assertEquals(contractMessages, AgentContract.MESSAGE_TYPES);

        List<String> contractEvents = asStringList(root.get("eventTypes"));
        assertEquals(contractEvents, AgentContract.EVENT_TYPES);

        List<String> contractPermissions = asStringList(root.get("permissions"));
        assertEquals(contractPermissions, AgentContract.PERMISSIONS);

        assertEquals(AgentContract.CONTRACT_VERSION, root.get("contractVersion").asText());
        assertEquals(AgentContract.SESSION_RETENTION_DAYS, root.get("session").get("retentionDays").asInt());

        List<String> contractStopReasons = asStringList(root.get("stopReasons"));
        assertEquals(contractStopReasons, AgentContract.STOP_REASONS);
    }

    private List<String> asStringList(JsonNode node) {
        List<String> result = new ArrayList<>();
        node.forEach(item -> result.add(item.asText()));
        return result;
    }
}
