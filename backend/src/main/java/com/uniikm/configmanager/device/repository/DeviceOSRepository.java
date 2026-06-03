package com.uniikm.configmanager.device.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uniikm.configmanager.device.model.DeviceOS;

import java.util.Optional;

public interface DeviceOSRepository extends JpaRepository<DeviceOS, Long> {
    
    Optional<DeviceOS> findByName(String name);
    
    @Query("SELECT os FROM DeviceOS os WHERE " +
           "(:name IS NULL OR os.name = :name) AND " +
           "(:vendor IS NULL OR os.vendor = :vendor) AND " +
           "(:model IS NULL OR os.model = :model)")
    Optional<DeviceOS> findByNameAndVendorAndModel(
            @Param("name") String name,
            @Param("vendor") String vendor,
            @Param("model") String model
    );
}