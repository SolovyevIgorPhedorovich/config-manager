// dto/LoginResponse.java
package com.uniikm.configmanager.auth.dto;

public record LoginResponse(String token, String refreshToken, String username) {}