package org.example.ai_study_notes.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.tool.ToolDefinition.ToolExecutor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.ai_study_notes.agent.tool.ToolRegistryEntryMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时注册所有内置工具。
 */
@Slf4j
@Component
public class ToolRegistrar {

    public ToolRegistrar(ToolRegistry registry, ToolRegistryEntryMapper entryMapper, List<ToolExecutor> executors) {
        for (ToolExecutor executor : executors) {
            try {
                registry.register(executor.definition());
            } catch (Exception e) {
                log.error("工具注册失败: {}", e.getMessage());
            }
        }
        // 恢复持久化的启停状态
        try {
            List<ToolRegistryEntry> entries = entryMapper.selectList(new LambdaQueryWrapper<>());
            for (ToolRegistryEntry entry : entries) {
                if (entry.getEnabled() != null && entry.getEnabled() == 0 && registry.contains(entry.getToolName())) {
                    registry.setEnabled(entry.getToolName(), false);
                }
            }
        } catch (Exception e) {
            log.warn("恢复工具启停状态失败: {}", e.getMessage());
        }
        log.info("Agent 工具注册完成，共 {} 个", registry.activeTools().size());
    }
}
