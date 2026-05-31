package com.project.configmanager.device.repository;

import java.net.InetAddress;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.configmanager.device.model.DeviceInfo;

public interface DeviceRepository extends JpaRepository<DeviceInfo, Long> {

    @Query("""
        select distinct d
        from DeviceInfo d
        left join fetch d.ips ip
        left join fetch d.group
        left join fetch d.osVersion
    """)
    boolean existsByIp(@Param("ip") String ip);

    @Query("""
        select distinct d
        from DeviceInfo d
        inner join fetch d.ips ip
        left join fetch d.group
        left join fetch d.osVersion
    """)
    Optional<DeviceInfo> findByIp(@Param("ip") String ip);

    Optional<DeviceInfo> findByHostname(String hostname);
}