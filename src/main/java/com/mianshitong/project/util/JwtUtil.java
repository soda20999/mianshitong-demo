package com.mianshitong.project.util;

import com.mianshitong.project.entity.bo.AuthUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

    public static final String ACCESS_TOKEN = "access";
    public static final String REFRESH_TOKEN = "refresh";
    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String KNOWN_WEAK_SECRET = "please-change-this-jwt-secret-minimum-32-bytes-long";

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-expire-minutes:${app.jwt.expire-minutes:720}}")
    private long accessExpireMinutes;

    @Value("${app.jwt.refresh-expire-days:7}")
    private long refreshExpireDays;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is required");
        }
        if (KNOWN_WEAK_SECRET.equals(secret)) {
            throw new IllegalStateException("JWT_SECRET must not use the known weak default value");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(AuthUser user) {
        return generateAccessToken(user);
    }

    public String generateAccessToken(AuthUser user) {
        return generateToken(user, ACCESS_TOKEN, Instant.now().plusSeconds(accessExpireMinutes * 60));
    }

    public String generateRefreshToken(AuthUser user) {
        return generateToken(user, REFRESH_TOKEN, Instant.now().plusSeconds(refreshExpireDays * 24 * 60 * 60));
    }

    public AuthUser parseToken(String token) {
        return parseToken(token, ACCESS_TOKEN);
    }

    public AuthUser parseToken(String token, String expectedType) {
        Claims claims = parseClaims(token, expectedType);
        Long userId = Long.valueOf(claims.getSubject());
        String role = claims.get("role", String.class);
        String email = claims.get("email", String.class);
        return new AuthUser(userId, role, email);
    }

    public Long parseUserId(String token) {
        return parseUserId(token, ACCESS_TOKEN);
    }

    public Long parseUserId(String token, String expectedType) {
        return Long.valueOf(parseClaims(token, expectedType).getSubject());
    }

    public Instant parseExpiration(String token) {
        Date expiration = parseClaims(token, null).getExpiration();
        return expiration == null ? Instant.now() : expiration.toInstant();
    }

    private String generateToken(AuthUser user, String tokenType, Instant expireAt) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(String.valueOf(user.userId()))
            .claim(TOKEN_TYPE_CLAIM, tokenType)
            .claim("role", user.role())
            .claim("email", user.email())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expireAt))
            .signWith(signingKey)
            .compact();
    }

    private Claims parseClaims(String token, String expectedType) {
        Claims claims = Jwts.parser().verifyWith(signingKey).build()
            .parseSignedClaims(token).getPayload();
        if (expectedType != null && !expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new IllegalArgumentException("Invalid JWT token type");
        }
        return claims;
    }
}
