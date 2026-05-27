package com.mianshitong.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mianshitong.project.common.exception.BizException;
import com.mianshitong.project.entity.bo.AuthUser;
import com.mianshitong.project.entity.dto.LoginRequest;
import com.mianshitong.project.entity.dto.RefreshTokenRequest;
import com.mianshitong.project.entity.dto.RegisterRequest;
import com.mianshitong.project.entity.dto.UpdateProfileRequest;
import com.mianshitong.project.entity.po.UserPo;
import com.mianshitong.project.entity.vo.LoginVo;
import com.mianshitong.project.entity.vo.UserVo;
import com.mianshitong.project.enum_.UserRole;
import com.mianshitong.project.mapper.UserMapper;
import com.mianshitong.project.service.AuthService;
import com.mianshitong.project.util.JwtUtil;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistSupport tokenBlacklistSupport;
    private final PasswordEncoder passwordEncoder;
    private final AuthAttemptSupport authAttemptSupport;

    @Override
    public LoginVo login(LoginRequest request, String clientIp) {
        String email = normalizeEmail(request.email());
        authAttemptSupport.assertLoginAllowed(email, clientIp);
        UserPo user = userMapper.selectOne(
            new LambdaQueryWrapper<UserPo>()
                .eq(UserPo::getEmail, email)
                .last("LIMIT 1")
        );
        if (user == null || !passwordMatches(user, request.password())) {
            authAttemptSupport.recordLoginFailure(email, clientIp);
            throw new BizException("账号或密码错误");
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BizException("账号已被禁用");
        }
        authAttemptSupport.clearLoginFailure(email, clientIp);
        return buildLoginVo(user);
    }

    @Override
    public LoginVo register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        Long count = userMapper.selectCount(new LambdaQueryWrapper<UserPo>().eq(UserPo::getEmail, email));
        if (count != null && count > 0) {
            throw new BizException("邮箱已注册");
        }
        UserPo user = new UserPo();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setAvatar("");
        user.setTargetPosition(request.targetPosition());
        user.setPoints(0);
        user.setEnabled(true);
        user.setRole(UserRole.USER);
        user.setCreatedAt(LocalDateTime.now());
        userMapper.insert(user);

        return buildLoginVo(user);
    }

    @Override
    public LoginVo refresh(RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();
        if (tokenBlacklistSupport.isBlacklisted(refreshToken)) {
            throw new BizException("refresh token 已失效");
        }
        Long userId;
        try {
            userId = jwtUtil.parseUserId(refreshToken, JwtUtil.REFRESH_TOKEN);
        } catch (Exception ex) {
            throw new BizException("refresh token 无效或已过期");
        }
        UserPo user = requireUser(userId);
        if (!Boolean.TRUE.equals(user.getEnabled()) || user.getRole() == null) {
            throw new BizException("账号已被禁用");
        }
        tokenBlacklistSupport.blacklist(refreshToken, jwtUtil.parseExpiration(refreshToken));
        return buildLoginVo(user);
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        blacklistToken(accessToken);
        blacklistToken(refreshToken);
    }

    private void blacklistToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            tokenBlacklistSupport.blacklist(token, jwtUtil.parseExpiration(token));
        } catch (Exception ignored) {
            // ignore invalid tokens
        }
    }

    @Override
    public UserVo getProfile(Long userId) {
        return toVo(requireUser(userId));
    }

    @Override
    public UserVo updateProfile(Long userId, UpdateProfileRequest request) {
        UserPo user = requireUser(userId);
        String avatar = request.avatar() == null ? "" : request.avatar().trim();
        if (!avatar.isEmpty() && !isAllowedAvatarValue(avatar)) {
            throw new BizException("头像仅支持 http(s) 链接或 data:image");
        }
        user.setNickname(request.nickname());
        user.setAvatar(avatar);
        user.setTargetPosition(request.targetPosition());
        userMapper.updateById(user);
        return toVo(user);
    }

    private boolean passwordMatches(UserPo user, String rawPassword) {
        String stored = user.getPassword();
        if (stored == null || stored.isBlank() || rawPassword == null) {
            return false;
        }
        if (isBcryptHash(stored)) {
            try {
                return passwordEncoder.matches(rawPassword, stored);
            } catch (Exception ex) {
                return false;
            }
        }
        if (!stored.equals(rawPassword)) {
            return false;
        }
        user.setPassword(passwordEncoder.encode(rawPassword));
        userMapper.updateById(user);
        return true;
    }

    private boolean isBcryptHash(String password) {
        return password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$");
    }

    private boolean isAllowedAvatarValue(String avatar) {
        return avatar.startsWith("https://")
            || avatar.startsWith("http://")
            || avatar.startsWith("data:image/");
    }

    private UserPo requireUser(Long userId) {
        UserPo user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return user;
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private UserVo toVo(UserPo user) {
        return new UserVo(
            user.getId(),
            user.getEmail(),
            user.getNickname(),
            user.getAvatar(),
            user.getTargetPosition(),
            user.getPoints(),
            user.getEnabled(),
            user.getRole(),
            user.getCreatedAt()
        );
    }

    private LoginVo buildLoginVo(UserPo user) {
        AuthUser authUser = new AuthUser(user.getId(), user.getRole().name(), user.getEmail());
        String accessToken = jwtUtil.generateAccessToken(authUser);
        String refreshToken = jwtUtil.generateRefreshToken(authUser);
        return new LoginVo(accessToken, accessToken, refreshToken, toVo(user));
    }
}
