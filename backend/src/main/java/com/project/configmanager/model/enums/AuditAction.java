package com.project.configmanager.model.enums;

public enum AuditAction {
    DEVICE_ADDED,
    CONFIG_APPLIED,
    CONFIG_ROLLED_BACK,
    DEVICE_UPDATED;

    public String getValue() {
        return name().replace('_', ' ');
    }
}