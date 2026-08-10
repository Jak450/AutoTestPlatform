package org.example.ai_study_notes;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan({
        "org.example.ai_study_notes.mapper",
        "org.example.ai_study_notes.agent.auth",
        "org.example.ai_study_notes.agent.session",
        "org.example.ai_study_notes.agent.confirmation"
})
@EnableScheduling
public class AiStudyNotesApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiStudyNotesApplication.class, args);
    }

}
