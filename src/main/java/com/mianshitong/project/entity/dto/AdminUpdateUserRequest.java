package com.mianshitong.project.entity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminUpdateUserRequest(
    @NotNull Boolean enabled,
    @Size(max = 100) String targetPosition
) {
}
