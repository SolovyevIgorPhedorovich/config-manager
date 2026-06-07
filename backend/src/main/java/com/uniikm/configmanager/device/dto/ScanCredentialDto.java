package com.uniikm.configmanager.device.dto;

import java.time.LocalDateTime;

/** Профиль доступа для отображения. Пароли НИКОГДА не возвращаются — только признак наличия. */
public record ScanCredentialDto(
        Long id,
        String name,
        String domain,
        String sshUsername,
        boolean hasSshPassword,
        String winrmUsername,
        boolean hasWinrmPassword,
        LocalDateTime createdAt
) {}
