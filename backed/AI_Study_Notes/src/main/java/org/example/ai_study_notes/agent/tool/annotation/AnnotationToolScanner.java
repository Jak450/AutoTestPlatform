package org.example.ai_study_notes.agent.tool.annotation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.tool.ToolDefinition;
import org.example.ai_study_notes.agent.tool.ToolRegistry;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 注解工具扫描器：启动完成后扫描所有 Spring bean 的 @AgentTool 方法并注册进 ToolRegistry。
 * 仅做编排；Schema 生成与反射执行分别由 AnnotationToolSchema / ToolMethodExecutor 负责。
 */
@Slf4j
@Component
public class AnnotationToolScanner implements ApplicationRunner {

    private final ApplicationContext applicationContext;
    private final ToolRegistry registry;
    private final ObjectMapper objectMapper;

    public AnnotationToolScanner(ApplicationContext applicationContext,
                                 ToolRegistry registry,
                                 ObjectMapper objectMapper) {
        this.applicationContext = applicationContext;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            scanBean(applicationContext.getBean(beanName));
        }
    }

    private void scanBean(Object bean) {
        for (Method method : bean.getClass().getMethods()) {
            AgentTool agentTool = method.getAnnotation(AgentTool.class);
            if (agentTool == null) {
                continue;
            }
            ToolDefinition definition = ToolDefinition.builder()
                    .name(agentTool.name())
                    .label(agentTool.label())
                    .description(agentTool.description())
                    .inputSchema(AnnotationToolSchema.generate(method))
                    .permission(agentTool.permission())
                    .category(agentTool.category())
                    .activeByDefault(agentTool.activeByDefault())
                    .version(agentTool.version())
                    .build();
            // 先建 definition，再注入执行器，保证 executor.definition() 完整
            definition.setExecutor(new ToolMethodExecutor(definition, bean, method, objectMapper));
            registry.register(definition);
            log.info("注解工具注册成功: {}", agentTool.name());
        }
    }
}
