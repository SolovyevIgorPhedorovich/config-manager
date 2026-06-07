package com.uniikm.configmanager.device.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uniikm.configmanager.device.model.ScanCredential;

public interface ScanCredentialRepository extends JpaRepository<ScanCredential, Long> {
    Optional<ScanCredential> findByName(String name);
    boolean existsByName(String name);
}
