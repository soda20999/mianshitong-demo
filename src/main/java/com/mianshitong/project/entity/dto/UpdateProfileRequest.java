package com.mianshitong.project.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @NotBlank @Size(max = 40) String nickname,
    @Size(max = 4_000_000) String avatar,
    @NotBlank @Size(max = 100) String targetPosition
) {
}
