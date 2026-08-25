package org.example.ai_study_notes.agent.memory.confirm;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 完成信号检测：识别"工作完成"类收尾语，触发批量确认提问。
 */
@Component
public class CompletionDetector {

    private static final List<String> SIGNALS = List.of(
            "完成了", "搞定了", "好了", "就这样", "先这样", "可以了", "结束了", "做完");

    public boolean isCompletionSignal(String userText) {
        if (userText == null || userText.isBlank()) {
            return false;
        }
        return SIGNALS.stream().anyMatch(userText::contains);
    }
}
