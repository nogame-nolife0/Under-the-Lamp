package org.example.backend_springboot.service;

import org.example.backend_springboot.dto.request.AuthForgotPasswordRequest;
import org.example.backend_springboot.dto.request.AuthLoginRequest;
import org.example.backend_springboot.dto.request.AuthRegisterRequest;
import org.example.backend_springboot.dto.vo.AuthForgotPasswordVO;
import org.example.backend_springboot.dto.vo.AuthLoginVO;
import org.example.backend_springboot.dto.vo.AuthProfileVO;
import org.example.backend_springboot.dto.vo.AuthStatusVO;

public interface AuthService {

    AuthStatusVO status();

    AuthLoginVO register(AuthRegisterRequest request);

    AuthLoginVO login(AuthLoginRequest request);

    AuthForgotPasswordVO forgotPassword(AuthForgotPasswordRequest request);

    AuthProfileVO profile(Long userId);

    void logout(String token);
}
