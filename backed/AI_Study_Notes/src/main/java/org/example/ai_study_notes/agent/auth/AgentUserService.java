package org.example.ai_study_notes.agent.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.agent.config.AgentProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Agent 用户服务：登录/登出/用户管理/默认管理员初始化。
 */
@Slf4j
@Service
public class AgentUserService {

    private static final String BLACKLIST_PREFIX = "auth:blacklist:";

    private final AgentUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AgentProperties properties;

    public AgentUserService(AgentUserMapper userMapper,
                            PasswordEncoder passwordEncoder,
                            JwtUtil jwtUtil,
                            RedisTemplate<String, Object> redisTemplate,
                            AgentProperties properties) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public Map<String, Object> login(String username, String rawPassword) {
        AgentUser user = findByUsername(username);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRole());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("user", toUserMap(user));
        return data;
    }

    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            long remaining = jwtUtil.getRemainingMillis(token);
            if (remaining > 0) {
                redisTemplate.opsForValue().set(BLACKLIST_PREFIX + token, "1", remaining, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            log.warn("登出黑名单写入失败: {}", e.getMessage());
        }
    }

    public Map<String, Object> me(Long userId) {
        AgentUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return toUserMap(user);
    }

    public AgentUser findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<AgentUser>()
                .eq(AgentUser::getUsername, username));
    }

    public void createUser(String username, String password, String displayName, String role) {
        if (findByUsername(username) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("密码长度不能少于 8 位");
        }
        String finalRole = (role == null || role.isBlank()) ? "user" : role;
        if (!"user".equals(finalRole) && !"admin".equals(finalRole)) {
            throw new IllegalArgumentException("角色只能是 user 或 admin");
        }
        AgentUser user = AgentUser.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .displayName(displayName == null ? "" : displayName)
                .role(finalRole)
                .status(1)
                .build();
        userMapper.insert(user);
    }

    public List<Map<String, Object>> listUsers() {
        List<AgentUser> users = userMapper.selectList(null);
        List<Map<String, Object>> result = new ArrayList<>();
        for (AgentUser user : users) {
            result.add(toUserMap(user));
        }
        return result;
    }

    public boolean isAdmin() {
        return "admin".equals(UserContext.role());
    }

    @PostConstruct
    public void initAdmin() {
        if (findByUsername("admin") != null) {
            return;
        }
        AgentUser admin = AgentUser.builder()
                .username("admin")
                .passwordHash(passwordEncoder.encode(properties.getAuth().getAdminPassword()))
                .displayName("系统管理员")
                .role("admin")
                .status(1)
                .build();
        userMapper.insert(admin);
        log.warn("已创建默认管理员账号 admin，请尽快修改默认密码（agent.auth.admin-password / ADMIN_INIT_PASSWORD）");
    }

    private Map<String, Object> toUserMap(AgentUser user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("displayName", user.getDisplayName());
        map.put("role", user.getRole());
        map.put("status", user.getStatus());
        map.put("createdAt", user.getCreatedAt());
        return map;
    }
}
