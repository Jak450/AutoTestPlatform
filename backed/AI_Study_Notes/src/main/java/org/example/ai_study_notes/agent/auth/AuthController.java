package org.example.ai_study_notes.agent.auth;

import lombok.Data;
import org.example.ai_study_notes.Pojo.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 认证接口：登录/登出/当前用户/用户管理。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AgentUserService userService;

    public AuthController(AgentUserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest request) {
        try {
            return Result.success(userService.login(request.getUsername(), request.getPassword()));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            token = authorization.substring(7).trim();
        }
        userService.logout(token);
        return Result.success();
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        Long userId = UserContext.userId();
        if (userId == null) {
            return Result.error("未登录");
        }
        return Result.success(userService.me(userId));
    }

    @GetMapping("/users")
    public Result<List<Map<String, Object>>> listUsers() {
        if (!userService.isAdmin()) {
            return Result.error("无权限，仅管理员可访问");
        }
        return Result.success(userService.listUsers());
    }

    @PostMapping("/users")
    public Result<Void> createUser(@RequestBody CreateUserRequest request) {
        if (!userService.isAdmin()) {
            return Result.error("无权限，仅管理员可访问");
        }
        try {
            userService.createUser(request.getUsername(), request.getPassword(), request.getDisplayName(), request.getRole());
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @Data
    public static class CreateUserRequest {
        private String username;
        private String password;
        private String displayName;
        private String role;
    }
}
