package com.project.configmanager.repository;

import com.project.configmanager.model.Device;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    boolean existsByIp(String ip);

    Optional<Device> findByHostname(String hostname);
}
