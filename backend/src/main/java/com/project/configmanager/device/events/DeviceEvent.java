package com.project.configmanager.device.events;

import com.project.configmanager.audit.enums.AuditAction;
import com.project.configmanager.device.model.DeviceInfo;

public record DeviceEvent(

        AuditAction action,

        DeviceInfo device,

        String actor,

        Object beforeState,

        Object afterState

) {}