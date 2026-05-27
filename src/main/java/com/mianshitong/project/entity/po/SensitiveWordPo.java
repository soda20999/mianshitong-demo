package com.mianshitong.project.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("xz_sensitive_word")
public class SensitiveWordPo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String word;
    private Boolean enabled;
    private LocalDateTime createdAt;
}
