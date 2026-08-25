package org.example.ai_study_notes.agent.memory.confirm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 确认回答解析：对/不对/改成X → 确认/拒绝/纠正（经验优先，其次关系）。
 */
@Slf4j
@Service
public class ConfirmationAnswerProcessor {

    private static final Pattern CORRECT_PATTERN =
            Pattern.compile("(?:改成|应该是|其实是|改为)[：:\\s]*(.+)");

    private final PendingConfirmationService pending;

    public ConfirmationAnswerProcessor(PendingConfirmationService pending) {
        this.pending = pending;
    }

    public int process(Long userId, String answer) {
        if (answer == null || answer.isBlank()) {
            return 0;
        }
        if (answer.contains("不对") || answer.contains("错了") || answer.contains("不是")) {
            Matcher matcher = CORRECT_PATTERN.matcher(answer);
            if (matcher.find()) {
                if (!pending.correctNextExperience(userId, matcher.group(1).trim())) {
                    pending.correctNextRelation(userId, matcher.group(1).trim());
                }
            } else {
                if (!pending.rejectNextExperience(userId)) {
                    pending.rejectNextRelation(userId);
                }
            }
            return 1;
        }
        if (answer.contains("对") || answer.contains("是的") || answer.contains("正确")
                || answer.contains("可以") || answer.contains("没问题")) {
            if (!pending.confirmNextExperience(userId)) {
                pending.confirmNextRelation(userId);
            }
            return 1;
        }
        return 0;
    }
}
