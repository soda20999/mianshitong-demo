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
@TableName(value = "xz_question_set", autoResultMap = true)
public class QuestionSetPo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long resumeId;
    private String jobTitle;
    private String direction;
    private String level;
    private String companyStyle;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<QuestionPo> questions;
    private LocalDateTime createdAt;
}
