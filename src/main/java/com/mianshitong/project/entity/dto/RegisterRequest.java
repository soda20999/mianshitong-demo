package com.mianshitong.project.entity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 6, max = 32) String password,
    @NotBlank @Size(max = 40) String nickname,
    @NotBlank @Size(max = 100) String targetPosition
) {
}
