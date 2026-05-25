package org.example.ai_study_notes.Pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApiRequestDTO {

    private String method;
    private String url;
    private String header;
    private String param;
    private String assertStr;
}
