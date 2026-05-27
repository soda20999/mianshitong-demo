package com.mianshitong.project.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
@TableName(value = "xz_jd_analysis", autoResultMap = true)
public class JdAnalysisPo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String jobTitle;
    private String jdContent;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> keywords;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> coreSkills;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> interviewFocuses;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> suggestions;
    private LocalDateTime createdAt;
}
