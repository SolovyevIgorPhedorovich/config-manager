// dto/LoginRequest.java
package com.uniikm.configmanager.auth.dto;

import lombok.Data;
import lombok.ToString;

@Data
public class LoginRequest {
    private String username;
    // Исключён из toString(), чтобы пароль не попадал в логи.
    @ToString.Exclude
    private String password;
    // authType при необходимости
}