package org.example.ai_study_notes.Aop.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Idempotent {
    int interval() default 5000;

    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;

    String message() default "请求已提交，请勿重复操作";
}
