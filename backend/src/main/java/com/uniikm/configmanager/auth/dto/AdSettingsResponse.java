package com.uniikm.configmanager.auth.dto;

/**
 * Настройки AD для интерфейса. Пароль наружу не отдаётся — только признак,
 * что он задан ({@code passwordSet}).
 */
public record AdSettingsResponse(
        boolean enabled,
        String url,
        String baseDn,
        String userDn,
        boolean passwordSet,
        String userSearchFilter
) {}
