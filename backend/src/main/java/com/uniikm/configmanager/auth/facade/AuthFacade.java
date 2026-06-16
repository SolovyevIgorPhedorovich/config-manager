// facade/AuthFacade.java
package com.uniikm.configmanager.auth.facade;

import java.util.Collection;
import java.util.Map;

import com.uniikm.configmanager.auth.dto.LoginRequest;
import com.uniikm.configmanager.auth.dto.LoginResponse;

public interface AuthFacade {
    LoginResponse login(LoginRequest request);
    /** Выдать новый access-токен по действующему refresh-токену (с ротацией refresh). */
    LoginResponse refresh(String refreshToken);
    /** Отозвать переданные access- и refresh-токены (помещение в чёрный список). */
    void logout(String accessToken, String refreshToken);
    String getCurrentUser();

    /**
     * Имена пользователей по их идентификаторам (id → username).
     * Контракт для других модулей (например, аудита), чтобы не обращаться
     * напрямую к {@code UserRepository}/{@code User}.
     */
    Map<Long, String> getUsernames(Collection<Long> userIds);
}