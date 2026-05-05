package com.project.configmanager.repository;

import com.project.configmanager.model.ConfigVersion;
import com.project.configmanager.model.device.DeviceInfo;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigVersionRepository extends JpaRepository<ConfigVersion, Long> {
    ConfigVersion findFirstByDeviceOrderByAppliedAtDesc(DeviceInfo device);
}
