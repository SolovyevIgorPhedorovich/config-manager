package com.uniikm.configmanager.config.enums;

import com.uniikm.configmanager.common.interfaces.CodeEnum;

public enum ConfigType implements CodeEnum {
    WINDOWS_SETTINGS(0),
    PROXMOX_VM_CONF(1),
    CISCO_RUNNING_CONFIG(2);

    private final int code;

    ConfigType(int code) {
        this.code = code;
    }

    @Override
    public int getCode() { return code; }

    public static ConfigType fromCode(int code) {
        for (ConfigType type : values()) {
            if (type.code == code) return type;
        }
        throw new IllegalArgumentException("Unknown config type code: " + code);
    }

    @Override
    public String toString() {
        return name().replace('_', ' ');
    }
}
