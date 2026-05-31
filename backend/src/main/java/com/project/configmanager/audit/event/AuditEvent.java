package com.project.configmanager.audit.event;

public record AuditEvent(
        String userId,
        String eventType,
        String aggregateType,
        Long aggregateId,
        Object beforeState,
        Object afterState
) {}