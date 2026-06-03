// dto/LoginRequest.java
package com.uniikm.configmanager.auth.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
    // authType при необходимости
}