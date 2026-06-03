package com.uniikm.configmanager.audit.enums;

import com.uniikm.configmanager.common.interfaces.CodeEnum;

public enum TaskStatus implements CodeEnum {
    FAILED(0),
    SUCCESS(1),
    QUEUED(2),
    RUNNING(3);

    private final int code;

    TaskStatus(int code) {
        this.code = code;
    }

    @Override
    public int getCode() { return code; }

    public static TaskStatus fromCode(int code) {
        for (TaskStatus status : values()) {
            if (status.code == code) return status;
        }
        throw new IllegalArgumentException("Unknown task status: " + code);
    }
}
