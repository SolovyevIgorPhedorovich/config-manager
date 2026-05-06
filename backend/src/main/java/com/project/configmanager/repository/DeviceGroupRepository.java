package com.project.configmanager.repository;

import com.project.configmanager.model.device.DeviceGroup;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceGroupRepository extends JpaRepository<DeviceGroup, Long> {
    Optional<DeviceGroup> findByName(String name);
}