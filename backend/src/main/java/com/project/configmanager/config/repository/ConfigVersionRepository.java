package com.project.configmanager.config.repository;

import com.project.configmanager.config.model.ConfigVersion;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigVersionRepository extends JpaRepository<ConfigVersion, Long> {
        Optional<ConfigVersion> findByChecksum(String checksum);

        ConfigVersion findTopByOrderByVersionNumDesc();
}
