package com.mianshitong.project.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StartInterviewRequest(
    @NotNull Long questionSetId,
    @NotBlank String style,
    @NotBlank String title
) {
}
