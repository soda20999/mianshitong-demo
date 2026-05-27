package com.mianshitong.project.entity.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminUpdatePromptRequest(
    @NotBlank String module,
    @NotBlank String name,
    @NotBlank String content
) {
}
