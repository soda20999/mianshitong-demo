package com.mianshitong.project.entity.dto;

import jakarta.validation.constraints.NotNull;

public record HotQuestionFavoriteRequest(
    @NotNull Boolean favorite
) {
}
