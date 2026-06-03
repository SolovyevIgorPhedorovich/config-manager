package com.uniikm.configmanager.audit.enums;

public enum AuditAction {
    DEVICE_ADDED,
    DEVICE_DELETED,
    DEVICE_UPDATED,

    CONFIG_APPLIED,
    CONFIG_ROLLED_BACK,

    LOGIN_SUCCESS,
    LOGIN_FAILURE;

    public String getValue() {
        return name().replace('_', ' ');
    }
}