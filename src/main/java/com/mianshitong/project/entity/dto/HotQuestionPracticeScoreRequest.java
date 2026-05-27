package com.mianshitong.project.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HotQuestionPracticeScoreRequest(
    @NotBlank @Size(max = 5000) String answer
) {
}
