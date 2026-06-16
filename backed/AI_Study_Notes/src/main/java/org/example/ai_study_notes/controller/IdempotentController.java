package org.example.ai_study_notes.controller;

import org.example.ai_study_notes.Pojo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/idempotent")
public class IdempotentController {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @GetMapping("/token")
    public Result<String> getToken() {
        String token = UUID.randomUUID().toString();
        return Result.success(token);
    }
}