package com.relex.wuzi.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireMillis;

    public JwtUtil(@Value("${wuzi.jwt.secret}") String secret,
                   @Value("${wuzi.jwt.expire-hours}") long expireHours) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "WUZI_JWT_SECRET 未配置：禁止使用空/默认密钥。请设置环境变量 WUZI_JWT_SECRET（至少 32 字节随机串）。");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("WUZI_JWT_SECRET 过短（至少 32 字符）");
        }
        if (secret.contains("test-jwt") || secret.contains("wuzi-test")) {
            throw new IllegalStateException("WUZI_JWT_SECRET 不得使用测试默认密钥");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 3600_000L;
    }

    public String createToken(Long userId, String username, List<String> roles) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claims(Map.of("username", username, "roles", roles))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
