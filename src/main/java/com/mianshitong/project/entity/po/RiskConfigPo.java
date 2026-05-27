package com.mianshitong.project.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("xz_risk_config")
public class RiskConfigPo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer rateLimitPerMinute;
    private Integer maxQuestionGeneratePerDay;
    private Integer maxReportGeneratePerDay;
    private Integer uploadMaxMb;
    private String uploadAllowTypes;
    private Integer inputMaxLength;
    private Boolean promptInjectionCheck;
    private Boolean outputSafetyCheck;
    private Boolean idempotencyCheck;
}
