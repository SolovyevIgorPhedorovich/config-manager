package com.uniikm.configmanager.config.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uniikm.configmanager.config.enums.ScheduledApplyStatus;
import com.uniikm.configmanager.config.model.ScheduledConfigApply;

public interface ScheduledConfigApplyRepository extends JpaRepository<ScheduledConfigApply, Long> {

    List<ScheduledConfigApply> findByStatus(ScheduledApplyStatus status);

    List<ScheduledConfigApply> findByDeviceIdAndStatus(Long deviceId, ScheduledApplyStatus status);

    List<ScheduledConfigApply> findByStatusOrderByCreatedAtDesc(ScheduledApplyStatus status);
}
