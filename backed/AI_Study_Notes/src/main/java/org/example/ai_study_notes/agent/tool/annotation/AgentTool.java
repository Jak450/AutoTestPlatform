package org.example.ai_study_notes.agent.tool.annotation;

import org.example.ai_study_notes.agent.contract.ToolPermission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级工具注解：标注在 Spring bean 的 public 方法上，启动时自动注册为 Agent 工具。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AgentTool {

    String name();

    String label() default "";

    String description() default "";

    ToolPermission permission() default ToolPermission.READ;

    String category() default "默认";

    boolean activeByDefault() default true;

    String version() default "1.0.0";
}
