package com.mianshitong.project.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.mianshitong.project.entity.vo.ResumeParseResultVo;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName(value = "xz_resume", autoResultMap = true)
public class ResumePo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String fileName;
    private String version;
    private String content;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private ResumeParseResultVo parseResult;
    private LocalDateTime uploadedAt;
}
