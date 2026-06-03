package com.uniikm.configmanager.config.dto;

import java.time.LocalDateTime;

public record TemplateAssignmentResponse(
        Long assignmentId,
        Long templateId,
        String templateName,
        Long deviceId,
        String deviceHostname,
        String deviceIp,
        String assignedBy,
        LocalDateTime assignedAt
) {}
