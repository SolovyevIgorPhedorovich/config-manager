package com.uniikm.configmanager.auth.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Пользователь для отображения. Пароль никогда не возвращается. */
public record UserResponse(
        Long id,
        String username,
        String email,
        boolean enabled,
        List<String> roles,
        LocalDateTime createdAt
) {}
