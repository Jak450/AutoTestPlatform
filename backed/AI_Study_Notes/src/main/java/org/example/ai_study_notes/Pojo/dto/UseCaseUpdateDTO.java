package org.example.ai_study_notes.Pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UseCaseUpdateDTO {
    private Integer id;

    private Integer pid;
    private String name;
    private String url;
    private String method;
    private String header;
    private String param;
    private String assertStr;
    private String desc;
}
