package com.mianshitong.project.entity.vo;

import com.mianshitong.project.enum_.UserRole;
import java.time.LocalDateTime;

public record UserVo(
    Long id,
    String email,
    String nickname,
    String avatar,
    String targetPosition,
    Integer points,
    Boolean enabled,
    UserRole role,
    LocalDateTime createdAt
) {
}
