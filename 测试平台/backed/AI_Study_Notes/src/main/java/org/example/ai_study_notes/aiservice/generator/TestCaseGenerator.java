package org.example.ai_study_notes.aiservice.generator;

import org.example.ai_study_notes.aiservice.context.DocContext;

import java.util.List;

public interface TestCaseGenerator {
    boolean supports(String type);
    String generate(DocContext context);
    List<String> validate(String generatedJson);
}