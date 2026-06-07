package com.uniikm.configmanager.audit.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.uniikm.configmanager.audit.model.EventLogEntity;

public interface AuditLogRepository extends JpaRepository<EventLogEntity, Long> {

    List<EventLogEntity> findByAggregateTypeAndAggregateIdOrderByCreatedAtDesc(
            String aggregateType, Long aggregateId);

    List<EventLogEntity> findByAggregateTypeAndAggregateIdInOrderByCreatedAtDesc(
            String aggregateType, Collection<Long> aggregateIds);

    List<EventLogEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}