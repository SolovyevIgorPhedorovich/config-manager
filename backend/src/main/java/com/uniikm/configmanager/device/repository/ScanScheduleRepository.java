package com.uniikm.configmanager.device.repository;

import com.uniikm.configmanager.device.model.ScanScheduleConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScanScheduleRepository extends JpaRepository<ScanScheduleConfig, Long> {
    List<ScanScheduleConfig> findAllByEnabledTrue();
}
