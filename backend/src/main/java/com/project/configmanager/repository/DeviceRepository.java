package com.project.configmanager.repository;

import java.net.InetAddress;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.configmanager.model.device.DeviceInfo;

public interface DeviceRepository extends JpaRepository<DeviceInfo, Long> {
    boolean  existsByIp(@Param("ip") String ip);
    @Query("SELECT d FROM DeviceInfo d JOIN d.ips ip WHERE ip.ip = :ip")
    Optional<DeviceInfo> findByIp(@Param("ip") String ip);

    Optional<DeviceInfo> findByHostname(String hostname);
}