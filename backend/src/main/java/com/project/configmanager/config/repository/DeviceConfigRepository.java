package com.project.configmanager.config.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.configmanager.config.model.DeviceConfig;

public interface DeviceConfigRepository extends JpaRepository<DeviceConfig, Long> {
    Optional<DeviceConfig> findByDeviceId(Long deviceId);
}
