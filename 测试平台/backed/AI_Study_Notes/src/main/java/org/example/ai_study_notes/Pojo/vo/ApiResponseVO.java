package org.example.ai_study_notes.Pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApiResponseVO {

    private Integer status;
    private Map<String, Object> result;
    private Map<String, String> headers;
    private Long time;
    private Long size;
    private List<AssertResult> assertResults;

}



