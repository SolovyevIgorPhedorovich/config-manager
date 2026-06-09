package com.uniikm.configmanager.auth.dto;

/**
 * Запрос на сохранение настроек AD из интерфейса.
 * Если {@code password} пустой/не задан — текущий пароль bind-учётки сохраняется.
 */
public record AdSettingsRequest(
        Boolean enabled,
        String url,
        String baseDn,
        String userDn,
        String password,
        String userSearchFilter
) {}
