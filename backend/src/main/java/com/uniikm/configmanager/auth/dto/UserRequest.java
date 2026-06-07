package com.uniikm.configmanager.auth.dto;

import java.util.List;

/** Создание/обновление пользователя. Пароль в открытом виде, хэшируется на сервере. */
public record UserRequest(
        String username,
        String password,
        String email,
        Boolean enabled,
        List<String> roles
) {}
