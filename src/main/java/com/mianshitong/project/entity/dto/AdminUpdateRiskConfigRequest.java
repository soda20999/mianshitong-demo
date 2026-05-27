package com.mianshitong.project.entity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminUpdateRiskConfigRequest(
    @NotNull @Min(1) @Max(500) Integer rateLimitPerMinute,
    @NotNull @Min(1) @Max(5000) Integer maxQuestionGeneratePerDay,
    @NotNull @Min(1) @Max(200) Integer maxReportGeneratePerDay,
    @NotNull @Min(1) @Max(100) Integer uploadMaxMb,
    @NotBlank String uploadAllowTypes,
    @NotNull @Min(100) @Max(100000) Integer inputMaxLength,
    @NotNull Boolean promptInjectionCheck,
    @NotNull Boolean outputSafetyCheck,
    @NotNull Boolean idempotencyCheck
) {
}
