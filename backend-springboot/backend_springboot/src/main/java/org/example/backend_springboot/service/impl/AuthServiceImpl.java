package org.example.backend_springboot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.backend_springboot.common.BizCode;
import org.example.backend_springboot.dto.request.AuthForgotPasswordRequest;
import org.example.backend_springboot.dto.request.AuthLoginRequest;
import org.example.backend_springboot.dto.request.AuthRegisterRequest;
import org.example.backend_springboot.dto.vo.AuthForgotPasswordVO;
import org.example.backend_springboot.dto.vo.AuthLoginVO;
import org.example.backend_springboot.dto.vo.AuthProfileVO;
import org.example.backend_springboot.dto.vo.AuthStatusVO;
import org.example.backend_springboot.entity.SysUser;
import org.example.backend_springboot.exception.BusinessException;
import org.example.backend_springboot.mapper.SysUserMapper;
import org.example.backend_springboot.service.AuthService;
import org.example.backend_springboot.service.AuthTokenService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final AuthTokenService authTokenService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthServiceImpl(SysUserMapper sysUserMapper, AuthTokenService authTokenService) {
        this.sysUserMapper = sysUserMapper;
        this.authTokenService = authTokenService;
    }

    @Override
    public AuthStatusVO status() {
        AuthStatusVO vo = new AuthStatusVO();
        vo.setRegistered(sysUserMapper.selectCount(null) > 0);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthLoginVO register(AuthRegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(BizCode.BAD_REQUEST, "两次输入的密码不一致");
        }

        String username = request.getUsername().trim();
        String phone = request.getPhone().trim();
        if (sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, phone)) > 0) {
            throw new BusinessException(BizCode.USER_ALREADY_EXISTS, "该手机号已注册");
        }
        if (sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)) > 0) {
            throw new BusinessException(BizCode.BAD_REQUEST, "用户名已被占用");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setPasswordPlain(request.getPassword());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.insert(user);
        return buildLoginVO(user);
    }

    @Override
    public AuthLoginVO login(AuthLoginRequest request) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, request.getPhone().trim()));
        if (user == null) {
            throw new BusinessException(BizCode.USER_NOT_FOUND, "手机号未注册");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(BizCode.PASSWORD_INCORRECT);
        }
        return buildLoginVO(user);
    }

    @Override
    public AuthForgotPasswordVO forgotPassword(AuthForgotPasswordRequest request) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, request.getPhone().trim()));
        if (user == null) {
            throw new BusinessException(BizCode.USER_NOT_FOUND, "该手机号未注册");
        }
        AuthForgotPasswordVO vo = new AuthForgotPasswordVO();
        vo.setPhone(user.getPhone());
        vo.setPassword(user.getPasswordPlain());
        return vo;
    }

    @Override
    public AuthProfileVO profile(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(BizCode.UNAUTHORIZED);
        }
        AuthProfileVO vo = new AuthProfileVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setPhone(user.getPhone());
        return vo;
    }

    @Override
    public void logout(String token) {
        authTokenService.removeToken(token);
    }

    private AuthLoginVO buildLoginVO(SysUser user) {
        AuthLoginVO vo = new AuthLoginVO();
        vo.setToken(authTokenService.createToken(user.getId()));
        vo.setUsername(user.getUsername());
        vo.setPhone(user.getPhone());
        return vo;
    }
}
