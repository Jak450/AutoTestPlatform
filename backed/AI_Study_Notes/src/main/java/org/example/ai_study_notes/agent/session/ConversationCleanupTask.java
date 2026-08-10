package org.example.ai_study_notes.agent.session;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.contract.AgentContract;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 会话保留 7 天，过期自动清理（每天凌晨 3 点执行）。
 */
@Slf4j
@Component
public class ConversationCleanupTask {

    private final ConversationService conversationService;

    public ConversationCleanupTask(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredConversations() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(AgentContract.SESSION_RETENTION_DAYS);
        conversationService.deleteExpired(cutoff);
        log.info("Agent 过期会话清理完成，cutoff={}", cutoff);
    }
}
