package com.project.configmanager.device.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.configmanager.device.model.DeviceOS;

public interface DeviceOSRepository extends JpaRepository <DeviceOS, Long> {
     Optional<DeviceOS> findByName(String name);
}
