package com.uniikm.configmanager.config.repository;

import com.uniikm.configmanager.config.model.ConfigTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConfigTemplateRepository extends JpaRepository<ConfigTemplate, Long> {

    List<ConfigTemplate> findAllByIsActiveTrue();

    Optional<ConfigTemplate> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);
}
