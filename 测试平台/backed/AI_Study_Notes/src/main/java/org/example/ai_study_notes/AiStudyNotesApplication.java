package org.example.ai_study_notes;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("org.example.ai_study_notes.mapper")
@EnableScheduling
public class AiStudyNotesApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiStudyNotesApplication.class, args);
    }

}
