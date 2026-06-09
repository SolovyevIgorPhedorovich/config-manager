// dto/mapper/AuthMapper.java
package com.uniikm.configmanager.auth.dto.mapper;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.uniikm.configmanager.auth.dto.LoginResponse;

@Component
public class AuthMapper {

    public LoginResponse toResponse(String accessToken, String refreshToken, Authentication authentication) {
        return new LoginResponse(accessToken, refreshToken, authentication.getName());
    }
}