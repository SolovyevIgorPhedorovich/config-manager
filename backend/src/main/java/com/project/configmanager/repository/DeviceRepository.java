package com.project.configmanager.repository;

import com.project.configmanager.model.Device;

import java.net.InetAddress;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    boolean  existsByIp(@Param("ip") String ip);
    @Query("select d from Device d join d.ip ip where ip.ip = :ip")
    Optional findByIp(@Param("ip") String ip);

    Optional<Device> findByHostname(String hostname);
}