package com.uniikm.configmanager.device.dto;

/** Создание/обновление профиля доступа. Пароли — в открытом виде, шифруются на сервере. */
public record ScanCredentialRequest(
        String name,
        String domain,
        String sshUsername,
        String sshPassword,
        String winrmUsername,
        String winrmPassword
) {}
