package com.mianshitong.project.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminAddSensitiveWordRequest(
    @NotBlank @Size(max = 60) String word
) {
}
