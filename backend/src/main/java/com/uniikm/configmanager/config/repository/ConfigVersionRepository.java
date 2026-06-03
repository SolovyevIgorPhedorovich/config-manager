package com.uniikm.configmanager.config.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uniikm.configmanager.config.model.ConfigVersion;

public interface ConfigVersionRepository extends JpaRepository<ConfigVersion, Long> {
        Optional<ConfigVersion> findByChecksum(String checksum);

        ConfigVersion findTopByOrderByVersionNumDesc();
}
