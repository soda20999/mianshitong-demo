package com.mianshitong.project.entity.dto;

import jakarta.validation.constraints.NotBlank;

public record HotQuestionActionRequest(
    @NotBlank String action
) {
}
