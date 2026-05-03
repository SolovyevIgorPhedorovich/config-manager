package com.project.configmanager.model.enums;

import com.project.configmanager.model.codes.CodeEnum;

public enum DeviceType implements CodeEnum {
    ПК(0),
    МФУ(1),
    CISCO(2),
    VM(3);

    private final int code;

    DeviceType(int code) {
        this.code = code;
    }

    @Override
    public int getCode() {
        return code;
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
