package org.example.ai_study_notes.agent.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JWT 认证过滤器：除登录接口外，所有 /api/* 请求必须携带有效 token。
 * SSE(EventSource) 不支持自定义请求头，因此额外支持 ?token= 查询参数。
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BLACKLIST_PREFIX = "auth:blacklist:";

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtUtil jwtUtil, RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        String path = request.getRequestURI();
        return "/api/auth/login".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null) {
            write401(response, "未登录，请先登录");
            return;
        }
        try {
            Claims claims = jwtUtil.parse(token);
            Boolean blacklisted = redisTemplate.hasKey(BLACKLIST_PREFIX + token);
            if (Boolean.TRUE.equals(blacklisted)) {
                write401(response, "登录已失效，请重新登录");
                return;
            }
            Object uidObj = claims.get("uid");
            Long userId = uidObj instanceof Number n ? n.longValue() : Long.valueOf(uidObj.toString());
            String username = claims.get("username", String.class);
            String role = claims.get("role", String.class);
            UserContext.set(new UserContext.CurrentUser(userId, username, role));
            chain.doFilter(request, response);
        } catch (Exception e) {
            write401(response, "token 无效或已过期，请重新登录");
        } finally {
            UserContext.clear();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            if (!token.isEmpty()) {
                return token;
            }
        }
        String queryToken = request.getParameter("token");
        return (queryToken == null || queryToken.isBlank()) ? null : queryToken.trim();
    }

    private void write401(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 0);
        body.put("msg", msg);
        body.put("data", null);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
