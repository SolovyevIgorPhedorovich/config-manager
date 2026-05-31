package com.project.configmanager.config.dto;

import lombok.Data;

@Data
public class DeviceCredentials {
    private Integer port;
    private String username;
    private String password;
    private String privateKey; // опционально для SSH-ключа
}
