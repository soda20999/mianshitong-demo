package com.mianshitong.project.entity.vo;

public record LoginVo(String token, String accessToken, String refreshToken, UserVo user) {

    public LoginVo(String token, UserVo user) {
        this(token, token, null, user);
    }
}
