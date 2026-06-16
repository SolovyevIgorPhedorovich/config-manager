package com.uniikm.configmanager.audit.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniikm.configmanager.audit.event.AuditEvent;
import com.uniikm.configmanager.audit.model.EventLogEntity;
import com.uniikm.configmanager.audit.repository.AuditLogRepository;
import com.uniikm.configmanager.auth.event.AuthEvent;
import com.uniikm.configmanager.auth.utils.SecurityFacade;
import com.uniikm.configmanager.device.event.DeviceEvent;

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

    @EventListener
    public void handle(DeviceEvent event) {
        EventLogEntity log = new EventLogEntity();
        log.setUserId(securityFacade.currentUserId());
        log.setEventType(event.action().name());
        log.setAggregateType("DEVICE");
        log.setAggregateId(event.device().getId());
        log.setPayload(objectMapper.valueToTree(event.beforeState()));
        log.setMetadata(objectMapper.valueToTree(event.afterState()));
        auditRepository.save(log);
    }

    @EventListener
    public void handle(AuthEvent event) {
        EventLogEntity log = new EventLogEntity();
        log.setUserId(securityFacade.currentUserId());
        log.setEventType(event.getAction().name());
        log.setAggregateType("AUTH");
        log.setPayload(objectMapper.valueToTree(event.getUsername()));
        log.setMetadata(objectMapper.valueToTree(event.getReason()));
        auditRepository.save(log);
    }
}