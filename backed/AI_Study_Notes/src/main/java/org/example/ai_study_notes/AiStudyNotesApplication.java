package org.example.ai_study_notes;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Repository;

@SpringBootApplication
@MapperScan(basePackages = {
        "org.example.ai_study_notes.mapper",
        "org.example.ai_study_notes.agent"
}, annotationClass = Repository.class)
@EnableScheduling
public class AiStudyNotesApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiStudyNotesApplication.class, args);
    }

}
