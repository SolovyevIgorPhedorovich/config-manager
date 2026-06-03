package com.uniikm.configmanager.device.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uniikm.configmanager.device.model.DeviceGroup;

import java.util.Optional;

public interface DeviceGroupRepository extends JpaRepository<DeviceGroup, Long> {
    Optional<DeviceGroup> findByName(String name);
}