package com.uniikm.configmanager.error;

import java.time.OffsetDateTime;

/**
 * Единый формат тела ответа об ошибке.
 * Поле {@code message} — краткое человекочитаемое описание для интерфейса.
 * Полная диагностика (стектрейс и т.п.) пишется в лог (/var/log/configmanager).
 */
public record ErrorResponse(
        String timestamp,
        int status,
        String error,
        String message,
        String path
) {
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(OffsetDateTime.now().toString(), status, error, message, path);
    }
}
