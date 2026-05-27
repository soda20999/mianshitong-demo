package com.mianshitong.project.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.mianshitong.project.enum_.InterviewStyle;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.Data;

@Data
@TableName(value = "xz_interview_session", autoResultMap = true)
public class InterviewSessionPo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long questionSetId;
    private String title;
    private InterviewStyle style;
    private String status;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<InterviewMessagePo> messages;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<ScoreDetailPo> scoreHistory;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Set<Long> favoriteQuestionIds;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Set<Long> wrongQuestionIds;
    private Integer totalScore;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
