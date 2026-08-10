package org.example.ai_study_notes.agent.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.example.ai_study_notes.agent.config.AgentProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 生成与解析工具。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireMinutes;

    public JwtUtil(AgentProperties properties) {
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("agent.jwt.secret 长度必须 >= 32 字节，请通过 JWT_SECRET 环境变量配置");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMinutes = properties.getJwt().getExpireMinutes();
    }

    public String generate(Long userId, String username, String role) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + expireMinutes * 60_000L);
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("uid", userId)
                .claim("username", username)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expire)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public long getRemainingMillis(String token) {
        Claims claims = parse(token);
        return Math.max(0, claims.getExpiration().getTime() - System.currentTimeMillis());
    }
}
