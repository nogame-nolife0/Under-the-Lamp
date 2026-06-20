package org.example.backend_springboot.service.impl;

import org.example.backend_springboot.service.AuthTokenService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class AuthTokenServiceImpl implements AuthTokenService {

    private static final String TOKEN_PREFIX = "pg:auth:token:";
    private static final Duration TOKEN_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;

    public AuthTokenServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String createToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(TOKEN_PREFIX + token, String.valueOf(userId), TOKEN_TTL);
        return token;
    }

    @Override
    public Long resolveUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String userId = redisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public void removeToken(String token) {
        if (token != null && !token.isBlank()) {
            redisTemplate.delete(TOKEN_PREFIX + token);
        }
    }
}
