package com.mianshitong.project.entity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record QuestionGenerateRequest(
    @NotNull Long resumeId,
    @NotBlank String jobTitle,
    @NotBlank String direction,
    @NotBlank String level,
    @NotBlank String companyStyle,
    @NotNull List<String> categories,
    @NotNull @Min(1) @Max(30) Integer count
) {
}
