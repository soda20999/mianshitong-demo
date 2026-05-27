package com.mianshitong.project.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("xz_prompt_template")
public class PromptTemplatePo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String module;
    private String name;
    private String content;
    private LocalDateTime updatedAt;
}
