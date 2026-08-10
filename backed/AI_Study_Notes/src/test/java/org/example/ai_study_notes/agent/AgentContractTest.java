package org.example.ai_study_notes.agent;

import org.example.ai_study_notes.agent.contract.AgentContract;
import org.example.ai_study_notes.agent.contract.MessageType;
import org.example.ai_study_notes.agent.contract.StopReason;
import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.contract.ToolResultMeta;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约文件与代码枚举双向校验（CONTRACT-1 基础版）。
 */
class AgentContractTest {

    @Test
    void messageTypesMatchContract() {
        assertEquals(AgentContract.MESSAGE_TYPES.size(), MessageType.values().length);
        for (String type : AgentContract.MESSAGE_TYPES) {
            assertTrue(Arrays.stream(MessageType.values()).anyMatch(e -> e.value().equals(type)),
                    "缺少消息类型: " + type);
        }
    }

    @Test
    void permissionsMatchContract() {
        assertEquals(AgentContract.PERMISSIONS.size(), ToolPermission.values().length);
        for (String permission : AgentContract.PERMISSIONS) {
            assertTrue(Arrays.stream(ToolPermission.values()).anyMatch(e -> e.value().equals(permission)),
                    "缺少权限: " + permission);
        }
    }

    @Test
    void stopReasonsMatchContract() {
        for (String reason : AgentContract.STOP_REASONS) {
            assertTrue(Arrays.stream(StopReason.values()).anyMatch(e -> e.value().equals(reason)),
                    "缺少终止原因: " + reason);
        }
    }

    @Test
    void toolResultMetaMatchesContract() {
        assertEquals(ToolResultMeta.Status.values().length, 3);
        assertEquals(ToolResultMeta.ErrorType.values().length, 13);
        assertEquals(ToolResultMeta.RecommendedNextAction.values().length, 5);
        assertEquals(ToolResultMeta.Source.values().length, 3);
    }

    @Test
    void confirmToolsRequireConfirmation() {
        assertTrue(ToolPermission.CONFIRM_EXECUTE.requiresConfirmation());
        assertTrue(ToolPermission.CONFIRM_WRITE.requiresConfirmation());
        assertTrue(!ToolPermission.READ.requiresConfirmation());
    }
}
