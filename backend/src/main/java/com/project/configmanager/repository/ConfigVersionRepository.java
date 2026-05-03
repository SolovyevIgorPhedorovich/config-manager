package com.project.configmanager.repository;

import com.project.configmanager.model.ConfigVersion;
import com.project.configmanager.model.Device;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigVersionRepository extends JpaRepository<ConfigVersion, Long> {
    ConfigVersion findFirstByDeviceOrderByAppliedAtDesc(Device device);
}
