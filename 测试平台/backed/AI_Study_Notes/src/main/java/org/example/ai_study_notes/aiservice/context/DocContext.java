package org.example.ai_study_notes.aiservice.context;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class DocContext {
    private String fileName;
    private String rawContent;
    private String parsedJson;
    private String skillContent;
    private Integer projectId;
    private String projectName;
    private List<QAEntry> qaHistory = new ArrayList<>();
    private String generatedCasesJson;
    private String analysisResult;

    @Data
    public static class QAEntry {
        private String question;
        private String answer;
    }

    public void addQA(String question, String answer) {
        QAEntry entry = new QAEntry();
        entry.setQuestion(question);
        entry.setAnswer(answer);
        qaHistory.add(entry);
    }

    public String buildQAPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("用户需求文档：\n").append(parsedJson).append("\n\n");
        if (!qaHistory.isEmpty()) {
            sb.append("用户问答记录：\n");
            for (int i = 0; i < qaHistory.size(); i++) {
                QAEntry qa = qaHistory.get(i);
                sb.append("Q").append(i + 1).append(": ").append(qa.getQuestion()).append("\n");
                sb.append("A").append(i + 1).append(": ").append(qa.getAnswer()).append("\n");
            }
        }
        return sb.toString();
    }
}