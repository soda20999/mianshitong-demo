package com.mianshitong.project.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JdAnalyzeRequest(
    @NotBlank @Size(max = 100) String jobTitle,
    @NotBlank @Size(max = 20000) String jdContent
) {
}
