package org.example.ai_study_notes.Pojo.vo;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AssertResult {
    private String field;
    private Object expected;
    private Object actual;
    private Boolean result;
}