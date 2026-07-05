package org.example.ai_study_notes.aiservice.agent.tool;

public interface Tool {
    String getName();
    String getDescription();
    String execute(String args);
}
