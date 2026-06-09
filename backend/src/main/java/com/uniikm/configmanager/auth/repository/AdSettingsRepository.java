package com.uniikm.configmanager.auth.repository;

import com.uniikm.configmanager.auth.model.AdSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdSettingsRepository extends JpaRepository<AdSettings, Long> {
}
