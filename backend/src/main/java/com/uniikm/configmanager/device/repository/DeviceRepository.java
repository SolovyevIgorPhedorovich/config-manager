package com.uniikm.configmanager.device.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uniikm.configmanager.device.model.DeviceInfo;

public interface DeviceRepository extends JpaRepository<DeviceInfo, Long> {

    @Query("""
        select case when count(d) > 0 then true else false end
        from DeviceInfo d
        join d.ips ip
        where ip.ip = :ip
    """)
    boolean existsByIp(@Param("ip") String ip);

    @Query("""
        select d
        from DeviceInfo d
        left join fetch d.ips ip
        left join fetch d.group
        left join fetch d.osVersion
        where ip.ip = :ip
    """)
    Optional<DeviceInfo> findByIp(@Param("ip") String ip);

    Optional<DeviceInfo> findByHostname(String hostname);
}