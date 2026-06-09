// facade/AuthFacade.java
package com.uniikm.configmanager.auth.facade;

import com.uniikm.configmanager.auth.dto.LoginRequest;
import com.uniikm.configmanager.auth.dto.LoginResponse;

public interface AuthFacade {
    LoginResponse login(LoginRequest request);
    /** Выдать новый access-токен по действующему refresh-токену (с ротацией refresh). */
    LoginResponse refresh(String refreshToken);
    /** Отозвать переданные access- и refresh-токены (помещение в чёрный список). */
    void logout(String accessToken, String refreshToken);
    String getCurrentUser();
}