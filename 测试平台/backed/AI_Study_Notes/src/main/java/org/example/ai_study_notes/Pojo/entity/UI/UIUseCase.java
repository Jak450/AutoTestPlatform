package org.example.ai_study_notes.Pojo.entity.UI;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ui_use_cases")
public class UIUseCase {


    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 所属项目ID
     */
    private String projectId;

    /**
     * 用例名称
     */
    private String name;

    /**
     * 用例描述
     */
    private String description;

    /**
     * 目标 URL
     */
    private String url;

    /**
     * 浏览器
     */
    private String browser;

    /**
     * 窗口大小
     */
    private String viewport;

    /**
     * 是否无头模式
     */
    private Boolean headless;

    /**
     * 超时时间（秒）
     */
    private Integer timeout;

    /**
     * 测试步骤 JSON 字符串
     */
    private String steps;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
