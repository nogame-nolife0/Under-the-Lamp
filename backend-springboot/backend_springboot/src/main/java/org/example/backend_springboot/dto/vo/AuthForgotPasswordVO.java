package org.example.backend_springboot.dto.vo;

import lombok.Data;

@Data
public class AuthForgotPasswordVO {

    private String phone;
    private String password;
}
