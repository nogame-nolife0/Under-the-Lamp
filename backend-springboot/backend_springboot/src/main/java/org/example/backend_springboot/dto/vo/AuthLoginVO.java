package org.example.backend_springboot.dto.vo;

import lombok.Data;

@Data
public class AuthLoginVO {

    private String token;
    private String username;
    private String phone;
}
