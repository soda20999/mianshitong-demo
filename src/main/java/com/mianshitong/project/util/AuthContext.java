package com.mianshitong.project.util;

import com.mianshitong.project.entity.bo.AuthUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthContext {

    private AuthContext() {
    }

    public static AuthUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser authUser)) {
            return null;
        }
        return authUser;
    }

    public static Long currentUserId() {
        AuthUser authUser = currentUser();
        return authUser == null ? null : authUser.userId();
    }

    public static boolean isAdmin() {
        AuthUser authUser = currentUser();
        return authUser != null && "ADMIN".equalsIgnoreCase(authUser.role());
    }
}
