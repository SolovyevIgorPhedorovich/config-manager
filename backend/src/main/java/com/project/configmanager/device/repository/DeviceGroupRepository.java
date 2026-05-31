package com.project.configmanager.device.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.configmanager.device.model.DeviceGroup;

public interface DeviceGroupRepository extends JpaRepository<DeviceGroup, Long> {
    Optional<DeviceGroup> findByName(String name);
}