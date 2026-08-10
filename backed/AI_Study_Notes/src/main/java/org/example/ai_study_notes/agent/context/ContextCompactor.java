package org.example.ai_study_notes.agent.context;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.core.AgentAiClient;
import org.example.ai_study_notes.agent.session.AgentConversation;
import org.example.ai_study_notes.agent.session.AgentMessage;
import org.example.ai_study_notes.agent.session.ConversationService;
import org.example.ai_study_notes.agent.session.MessageService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 主动分层压缩：把窗口前的历史交给 LLM 生成结构化摘要，写入会话 context_summary。
 * 摘要以系统消息注入，不删除历史消息（保证数据完整）。
 */
@Slf4j
@Service
public class ContextCompactor {

    private static final int KEEP_MESSAGES = 40;
    private static final int TRIGGER_MESSAGES = 80;
    private static final int TRIGGER_CHARS = 120_000;

    private static final String SUMMARIZE_PROMPT = """
            你是会话摘要器。请把对话历史压缩成结构化中文摘要，只输出以下小节：
            目标 / 约束 / 进度 / 关键决策 / 下一步 / 关键上下文
            每节 1-3 行，不要输出其他内容。
            """;

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final AgentAiClient aiClient;

    public ContextCompactor(ConversationService conversationService,
                            MessageService messageService,
                            AgentAiClient aiClient) {
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.aiClient = aiClient;
    }

    public boolean needsCompaction(Long conversationId) {
        List<AgentMessage> messages = messageService.list(conversationId);
        int chars = 0;
        for (AgentMessage message : messages) {
            if (message.getContent() != null) {
                chars += message.getContent().length();
            }
        }
        return messages.size() > TRIGGER_MESSAGES || chars > TRIGGER_CHARS;
    }

    /**
     * 压缩：对保留窗口之前的消息生成摘要并写入会话。
     */
    public String compact(Long conversationId) {
        List<AgentMessage> messages = messageService.list(conversationId);
        if (messages.size() <= KEEP_MESSAGES) {
            return "消息数量不足，无需压缩";
        }
        int cut = messages.size() - KEEP_MESSAGES;
        // 压缩点不允许落在 tool_result 上：向前调整到完整消息对之后
        while (cut < messages.size() && "tool".equals(messages.get(cut).getRole())) {
            cut++;
        }
        if (cut >= messages.size()) {
            cut = messages.size() - KEEP_MESSAGES;
        }
        StringBuilder history = new StringBuilder();
        for (int i = 0; i < cut; i++) {
            AgentMessage message = messages.get(i);
            history.append('[').append(message.getRole()).append('/').append(message.getType()).append("] ")
                    .append(truncate(message.getContent(), 2000)).append('\n');
        }
        String summary;
        try {
            summary = aiClient.chat(SUMMARIZE_PROMPT, history.toString());
        } catch (Exception e) {
            log.warn("LLM 摘要失败，退回截断摘要: {}", e.getMessage());
            summary = fallbackSummary(history.toString());
        }
        AgentConversation update = new AgentConversation();
        update.setId(conversationId);
        update.setContextSummary(summary);
        conversationService.update(update);
        log.info("会话 {} 压缩完成：摘要 {} 字符，保留 {} 条消息", conversationId, summary.length(), KEEP_MESSAGES);
        return summary;
    }

    private String fallbackSummary(String history) {
        String trimmed = truncate(history, 4000);
        return "目标：\n" + trimmed + "\n（LLM 摘要不可用时的截断历史）";
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) : text;
    }
}
