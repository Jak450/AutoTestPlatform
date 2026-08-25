package org.example.ai_study_notes.agent.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具方法参数注解：覆盖参数名与描述，控制是否必填。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ToolParam {

    String name() default "";

    String description() default "";

    boolean required() default true;
}
