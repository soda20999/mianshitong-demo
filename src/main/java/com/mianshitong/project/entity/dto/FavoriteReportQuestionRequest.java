package com.mianshitong.project.entity.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record FavoriteReportQuestionRequest(
    @NotNull @Min(0) Integer questionIndex
) {
}
