package com.uniikm.configmanager.audit.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uniikm.configmanager.audit.model.EventLogEntity;

public interface AuditLogRepository extends JpaRepository<EventLogEntity, Long> {
    
}