package com.project.configmanager.audit.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.configmanager.audit.event.AuditEvent;
import com.project.configmanager.audit.model.EventLogEntity;
import com.project.configmanager.audit.repository.AuditLogRepository;
import com.project.configmanager.auth.utils.SecurityFacade;

import lombok.RequiredArgsConstructor;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditLogRepository auditRepository;
    private final SecurityFacade securityFacade;
    private final ObjectMapper objectMapper;

    @EventListener
    public void handle(AuditEvent event) {

        EventLogEntity log = new EventLogEntity();

        log.setUserId(securityFacade.currentUserId());
        log.setEventType(event.eventType());
        log.setAggregateId(event.aggregateId());
        log.setAggregateType(event.aggregateType());

        log.setPayload(objectMapper.valueToTree(event.beforeState()));
        log.setMetadata(objectMapper.valueToTree(event.afterState()));

        auditRepository.save(log);
    }
}