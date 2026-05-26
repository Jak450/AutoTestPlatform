package org.example.ai_study_notes.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 指向 Maven target 目录中的 Allure 报告
        registry.addResourceHandler("/allure-report/**")
                .addResourceLocations("file:target/allure-report/");

//        // 如果需要，也可以添加 classpath 路径作为备选
//        registry.addResourceHandler("/allure/**")
//                .addResourceLocations("classpath:/allure-report/");
    }
}