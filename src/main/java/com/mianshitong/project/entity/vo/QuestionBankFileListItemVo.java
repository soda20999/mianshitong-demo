package com.mianshitong.project.entity.vo;

import java.time.LocalDateTime;

public record QuestionBankFileListItemVo(
    Long id,
    String fileName,
    String fileType,
    LocalDateTime uploadedAt
) {
}
