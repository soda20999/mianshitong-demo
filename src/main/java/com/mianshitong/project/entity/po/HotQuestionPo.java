package com.mianshitong.project.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("xz_hot_question")
public class HotQuestionPo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long bankFileId;
    private String position;
    private String tag;
    private String content;
    private String answer;
    private Long views;
    private Long favorites;
    private Long practices;
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private Boolean favorited;
}
