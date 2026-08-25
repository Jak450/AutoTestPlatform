package org.example.ai_study_notes.agent.tool.tools.annotated;

import org.example.ai_study_notes.agent.contract.ToolPermission;
import org.example.ai_study_notes.agent.memory.graph.RelationExtractor;
import org.example.ai_study_notes.agent.tool.ToolContext;
import org.example.ai_study_notes.agent.tool.annotation.AgentTool;
import org.example.ai_study_notes.agent.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 注解化工具示例：把业务文本中的实体关系抽取入库（候选，需人工确认后参与注入）。
 */
@Component
public class RelationToolGroup {

    private final RelationExtractor extractor;

    public RelationToolGroup(RelationExtractor extractor) {
        this.extractor = extractor;
    }

    @AgentTool(name = "extract_business_relations", label = "抽取业务关系",
               description = "从需求文档/对话文本中抽取业务实体之间的关系并入库（候选，需人工确认后生效）",
               permission = ToolPermission.CONFIRM_WRITE, category = "知识")
    public Map<String, Object> extract(
            @ToolParam(name = "content", description = "待抽取的业务文本") String content,
            ToolContext context) {
        int saved = extractor.extract(content, context.getUserId());
        return Map.of("saved", saved);
    }
}
