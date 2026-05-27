package com.mianshitong.project.entity.bo;

public record AiUsage(int promptTokens, int completionTokens) {

    public static AiUsage empty() {
        return new AiUsage(0, 0);
    }

    public AiUsage plus(AiUsage other) {
        if (other == null) {
            return this;
        }
        return new AiUsage(
            Math.max(0, promptTokens + other.promptTokens),
            Math.max(0, completionTokens + other.completionTokens)
        );
    }
}
