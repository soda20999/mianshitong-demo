package com.mianshitong.project.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResumeUploadRequest(
    @NotBlank @Size(max = 120) String fileName,
    @NotBlank @Size(max = 30) String version,
    @NotBlank @Size(max = 20000) String content
) {
}
