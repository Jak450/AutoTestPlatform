package org.example.ai_study_notes.Pojo.dto.UiDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 描述单个断言信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UiAssertionDTO {

    /**
     * 断言类型: equals / contains / notEmpty
     */
    private String type;

    /**
     * 期望值（notEmpty 类型可为空）
     */
    private String expected;
}

