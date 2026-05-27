package com.mianshitong.project.service;

import com.mianshitong.project.entity.dto.LoginRequest;
import com.mianshitong.project.entity.dto.RefreshTokenRequest;
import com.mianshitong.project.entity.dto.RegisterRequest;
import com.mianshitong.project.entity.dto.UpdateProfileRequest;
import com.mianshitong.project.entity.vo.LoginVo;
import com.mianshitong.project.entity.vo.UserVo;

public interface AuthService {
    LoginVo login(LoginRequest request, String clientIp);

    LoginVo register(RegisterRequest request);

    LoginVo refresh(RefreshTokenRequest request);

    void logout(String accessToken, String refreshToken);

    UserVo getProfile(Long userId);

    UserVo updateProfile(Long userId, UpdateProfileRequest request);
}
