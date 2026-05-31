package com.project.configmanager.config.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.configmanager.device.model.DeviceInfo;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name = "config_versions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"device_id", "version_num"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfigVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_num", nullable = false)
    private Integer versionNum;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_data", nullable = false, columnDefinition = "jsonb")
    private JsonNode configData;

    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_version_id")
    private ConfigVersion parentVersion;

    
}