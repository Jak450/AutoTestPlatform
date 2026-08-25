package org.example.ai_study_notes.agent.memory.retrieval;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 注入组装：按分数排序、预算截断、必选条目优先。
 */
public final class InjectionAssembler {

    private InjectionAssembler() {
    }

    public static String assemble(Map<String, Double> scores, Map<String, String> idToContent,
                                  int maxChars, List<String> mustInclude) {
        StringBuilder sb = new StringBuilder();
        for (String id : mustInclude) {
            String content = idToContent.get(id);
            if (content != null) {
                sb.append(content).append('\n');
            }
        }
        List<String> sorted = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
        for (String id : sorted) {
            if (mustInclude.contains(id)) {
                continue;
            }
            String content = idToContent.get(id);
            if (content == null) {
                continue;
            }
            if (sb.length() + content.length() + 1 > maxChars) {
                break;
            }
            sb.append(content).append('\n');
        }
        return sb.toString();
    }
}
