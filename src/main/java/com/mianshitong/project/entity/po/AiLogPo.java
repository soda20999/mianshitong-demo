package com.mianshitong.project.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("xz_ai_log")
public class AiLogPo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String module;
    private Integer promptTokens;
    private Integer completionTokens;
    private BigDecimal cost;
    private String status;
    @TableField("log_time")
    private LocalDateTime time;
}
