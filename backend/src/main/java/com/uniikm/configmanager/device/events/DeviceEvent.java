package com.uniikm.configmanager.device.events;

import com.uniikm.configmanager.audit.enums.AuditAction;
import com.uniikm.configmanager.device.model.DeviceInfo;

public record DeviceEvent(

        AuditAction action,

        DeviceInfo device,

        Object beforeState,

        Object afterState

) {}