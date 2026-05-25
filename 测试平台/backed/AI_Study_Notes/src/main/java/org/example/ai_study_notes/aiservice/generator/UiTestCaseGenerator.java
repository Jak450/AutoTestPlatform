package org.example.ai_study_notes.aiservice.generator;

import org.example.ai_study_notes.aiservice.context.DocContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UiTestCaseGenerator implements TestCaseGenerator {

    @Override
    public boolean supports(String type) {
        return "UI".equals(type);
    }

    @Override
    public String generate(DocContext context) {
        throw new UnsupportedOperationException("UI 测试用例生成尚未实现");
    }

    @Override
    public List<String> validate(String generatedJson) {
        throw new UnsupportedOperationException("UI 测试用例生成尚未实现");
    }
}