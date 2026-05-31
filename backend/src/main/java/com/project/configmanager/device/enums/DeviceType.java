package com.project.configmanager.device.enums;

import com.project.configmanager.common.interfaces.CodeEnum;

public enum DeviceType implements CodeEnum {
    WINDOWS(0),
    LINUX(1),
    МФУ(2),
    CISCO(3),
    PROXMOX(4);

    private final int code;

    DeviceType(int code) {
        this.code = code;
    }

    @Override
    public int getCode() {
        return code;
    }

    public String getString() {
        return this.toString();
    }

    public static DeviceType fromCode(int code) {
        for (DeviceType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown device type code: " + code);
    }

    @Override
    public String toString() {
        return name().replace("_", " ");
    }
}
