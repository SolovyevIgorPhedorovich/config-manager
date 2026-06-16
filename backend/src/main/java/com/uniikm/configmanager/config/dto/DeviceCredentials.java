package com.uniikm.configmanager.config.dto;

import lombok.Data;
import lombok.ToString;

@Data
public class DeviceCredentials {
    private Integer port;
    private String username;
    // Секреты исключены из toString(), чтобы не попадали в логи.
    @ToString.Exclude
    private String password;
    @ToString.Exclude
    private String privateKey;
    @ToString.Exclude
    private String community;
}
