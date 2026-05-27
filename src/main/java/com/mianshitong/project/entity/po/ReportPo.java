package com.mianshitong.project.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.mianshitong.project.enum_.TaskStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@TableName(value = "xz_report", autoResultMap = true)
public class ReportPo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long interviewId;
    private TaskStatus status;
    private Integer overallScore;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Integer> dimensions;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> weakPoints;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> reviewRoadmap;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> questionList;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> userAnswerHighlights;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> aiStandardAnswers;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> brightSpots;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
