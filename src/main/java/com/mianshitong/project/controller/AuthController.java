package com.mianshitong.project.controller;

import com.mianshitong.project.common.constant.SecurityConstants;
import com.mianshitong.project.common.result.ApiResult;
import com.mianshitong.project.entity.dto.LoginRequest;
import com.mianshitong.project.entity.dto.RefreshTokenRequest;
import com.mianshitong.project.entity.dto.RegisterRequest;
import com.mianshitong.project.entity.dto.UpdateProfileRequest;
import com.mianshitong.project.entity.vo.LoginVo;
import com.mianshitong.project.entity.vo.UserVo;
import com.mianshitong.project.service.AuthService;
import com.mianshitong.project.util.AuthContext;
import com.mianshitong.project.util.ClientIpUtil;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResult<LoginVo> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ApiResult.ok(authService.login(request, ClientIpUtil.resolve(httpRequest)));
    }

    @PostMapping("/register")
    public ApiResult<LoginVo> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResult.ok(authService.register(request));
    }

    @PostMapping("/refresh")
    public ApiResult<LoginVo> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResult.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ApiResult<Map<String, Boolean>> logout(
        HttpServletRequest request,
        @RequestBody(required = false) RefreshTokenRequest body
    ) {
        String authHeader = request.getHeader(SecurityConstants.AUTH_HEADER);
        String accessToken = null;
        if (authHeader != null && authHeader.startsWith(SecurityConstants.BEARER_PREFIX)) {
            accessToken = authHeader.substring(SecurityConstants.BEARER_PREFIX.length());
        }
        String refreshToken = body == null ? null : body.refreshToken();
        authService.logout(accessToken, refreshToken);
        return ApiResult.ok(Map.of("loggedOut", true));
    }

    @GetMapping("/profile")
    public ApiResult<UserVo> profile() {
        return ApiResult.ok(authService.getProfile(AuthContext.currentUserId()));
    }

    @PutMapping("/profile")
    public ApiResult<UserVo> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResult.ok(authService.updateProfile(AuthContext.currentUserId(), request));
    }
}
