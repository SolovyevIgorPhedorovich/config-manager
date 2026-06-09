package com.uniikm.configmanager.auth.dto;

/** Тело запроса обновления/отзыва токена. */
public record RefreshRequest(String refreshToken) {}
