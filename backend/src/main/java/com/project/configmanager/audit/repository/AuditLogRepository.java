package com.project.configmanager.audit.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.configmanager.audit.model.EventLogEntity;

public interface AuditLogRepository extends JpaRepository<EventLogEntity, Long> {
    
}