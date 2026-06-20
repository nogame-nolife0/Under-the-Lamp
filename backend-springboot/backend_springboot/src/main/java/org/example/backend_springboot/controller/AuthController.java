package org.example.backend_springboot.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.backend_springboot.common.Result;
import org.example.backend_springboot.dto.request.AuthForgotPasswordRequest;
import org.example.backend_springboot.dto.request.AuthLoginRequest;
import org.example.backend_springboot.dto.request.AuthRegisterRequest;
import org.example.backend_springboot.dto.vo.AuthForgotPasswordVO;
import org.example.backend_springboot.dto.vo.AuthLoginVO;
import org.example.backend_springboot.dto.vo.AuthProfileVO;
import org.example.backend_springboot.dto.vo.AuthStatusVO;
import org.example.backend_springboot.service.AuthService;
import org.example.backend_springboot.util.AuthContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/status")
    public Result<AuthStatusVO> status() {
        return Result.success(authService.status());
    }

    @PostMapping("/register")
    public Result<AuthLoginVO> register(@Valid @RequestBody AuthRegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @PostMapping("/login")
    public Result<AuthLoginVO> login(@Valid @RequestBody AuthLoginRequest request) {
        return Result.success(authService.login(request));
    }

    @PostMapping("/forgot-password")
    public Result<AuthForgotPasswordVO> forgotPassword(@Valid @RequestBody AuthForgotPasswordRequest request) {
        return Result.success(authService.forgotPassword(request));
    }

    @GetMapping("/me")
    public Result<AuthProfileVO> profile() {
        return Result.success(authService.profile(AuthContext.getUserId()));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        authService.logout(extractToken(request));
        return Result.success();
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }
}
