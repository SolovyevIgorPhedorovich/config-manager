package com.uniikm.configmanager.device.enums;

import com.uniikm.configmanager.common.interfaces.CodeEnum;

public enum DeviceType implements CodeEnum {
    PC(0),       // ПК: Windows или Linux — конкретная ОС определяется по DeviceOS
    МФУ(1),
    CISCO(2),
    PROXMOX(3);  // виртуальные машины (VM)

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
