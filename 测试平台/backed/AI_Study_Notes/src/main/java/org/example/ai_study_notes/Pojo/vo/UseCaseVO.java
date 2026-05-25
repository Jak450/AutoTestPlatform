package org.example.ai_study_notes.Pojo.vo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UseCaseVO {

    private Integer id;
    private String name;
    private String url;
    private String method;
    private String description;
}
