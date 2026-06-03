package com.uniikm.configmanager.config.repository;

import com.uniikm.configmanager.config.model.TemplateAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TemplateAssignmentRepository extends JpaRepository<TemplateAssignment, Long> {

    @Query("""
        SELECT ta FROM TemplateAssignment ta
        JOIN FETCH ta.device d
        LEFT JOIN FETCH d.ips
        WHERE ta.template.id = :templateId
        """)
    List<TemplateAssignment> findByTemplateId(@Param("templateId") Long templateId);

    @Query("""
        SELECT ta FROM TemplateAssignment ta
        JOIN FETCH ta.template
        WHERE ta.device.id = :deviceId
        """)
    List<TemplateAssignment> findByDeviceId(@Param("deviceId") Long deviceId);

    Optional<TemplateAssignment> findByTemplate_IdAndDevice_Id(Long templateId, Long deviceId);

    boolean existsByTemplate_IdAndDevice_Id(Long templateId, Long deviceId);

    void deleteByTemplate_IdAndDevice_Id(Long templateId, Long deviceId);

    int countByTemplate_Id(Long templateId);
}
