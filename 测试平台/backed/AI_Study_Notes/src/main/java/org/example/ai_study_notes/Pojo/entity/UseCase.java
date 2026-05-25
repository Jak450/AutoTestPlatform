package org.example.ai_study_notes.Pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("use_case")
public class UseCase {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer pid;
    private String name;
    private String url;
    private String method;
    private String header;
    private String param;
    private String assertStr;
    private String description;
}
