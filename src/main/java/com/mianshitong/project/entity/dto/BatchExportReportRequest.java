package com.mianshitong.project.entity.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BatchExportReportRequest(
    @NotEmpty @Size(max = 50) List<@NotNull @Min(1) Long> reportIds
) {
}

