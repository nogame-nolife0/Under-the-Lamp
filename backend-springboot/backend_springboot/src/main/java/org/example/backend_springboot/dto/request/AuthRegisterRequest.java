package org.example.backend_springboot.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuthRegisterRequest {

    @NotBlank(message = "请输入用户名")
    @Size(min = 2, max = 20, message = "用户名长度为 2~20 个字符")
    private String username;

    @NotBlank(message = "请输入手机号")
    @Pattern(regexp = "^1\\d{10}$", message = "请输入正确的 11 位手机号")
    private String phone;

    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 32, message = "密码长度为 6~32 位")
    private String password;

    @NotBlank(message = "请确认密码")
    private String confirmPassword;
}
