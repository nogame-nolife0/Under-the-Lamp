package org.example.backend_springboot.service;

public interface AuthTokenService {

    String createToken(Long userId);

    Long resolveUserId(String token);

    void removeToken(String token);
}
