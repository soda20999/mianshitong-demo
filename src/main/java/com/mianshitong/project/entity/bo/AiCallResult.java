package com.mianshitong.project.entity.bo;

public record AiCallResult<T>(T data, AiUsage usage) {

    public AiCallResult {
        usage = usage == null ? AiUsage.empty() : usage;
    }
}
