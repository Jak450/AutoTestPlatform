package org.example.ai_study_notes.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.tool.ToolDefinition.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时注册所有内置工具。
 */
@Slf4j
@Component
public class ToolRegistrar {

    public ToolRegistrar(ToolRegistry registry, List<ToolExecutor> executors) {
        for (ToolExecutor executor : executors) {
            try {
                registry.register(executor.definition());
            } catch (Exception e) {
                log.error("工具注册失败: {}", e.getMessage());
            }
        }
        log.info("Agent 工具注册完成，共 {} 个", registry.activeTools().size());
    }
}
