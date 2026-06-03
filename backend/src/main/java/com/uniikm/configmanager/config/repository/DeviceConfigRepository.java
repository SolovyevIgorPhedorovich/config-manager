package com.uniikm.configmanager.config.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.uniikm.configmanager.config.model.ConfigVersion;
import com.uniikm.configmanager.config.model.DeviceConfig;

import io.lettuce.core.dynamic.annotation.Param;

public interface DeviceConfigRepository extends JpaRepository<DeviceConfig, Long> {
    Optional<DeviceConfig> findByDeviceId(Long deviceId);

    @Query(value = """
        WITH RECURSIVE version_chain AS (
            SELECT cv.* FROM config_versions cv
            JOIN device_config dc ON dc.config_id = cv.id
            WHERE dc.device_id = :deviceId
            UNION ALL
            SELECT cv.* FROM config_versions cv
            JOIN version_chain vc ON cv.id = vc.parent_version_id
        )
        SELECT * FROM version_chain ORDER BY created_at ASC
        """, nativeQuery = true)
    List<ConfigVersion> findFullHistoryByDeviceId(@Param("deviceId") Long deviceId);
}
