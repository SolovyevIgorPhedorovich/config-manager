package com.project.configmanager.service;

import com.project.configmanager.model.AuditLog;
import com.project.configmanager.model.device.DeviceInfo;
import com.project.configmanager.model.enums.AuditAction;
import com.project.configmanager.model.enums.TaskStatus;
import com.project.configmanager.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogger {

    private final AuditLogRepository auditLogRepository;

    public AuditLog logSystemAction(
        AuditAction action,
        DeviceInfo targetDevice,
        String targetIp,
        String oldConfig,
        String newConfig,
        TaskStatus status
    ) {
        AuditLog auditLog = AuditLog.builder()
            .userId("system")
            .actionType(action)
            .targetDevice(targetDevice)
            .targetIp(targetIp)
            .oldConfig(oldConfig)
            .newConfig(newConfig)
            .statusValue(status.getCode())
            .build();

        return auditLogRepository.save(auditLog);
    }
}
