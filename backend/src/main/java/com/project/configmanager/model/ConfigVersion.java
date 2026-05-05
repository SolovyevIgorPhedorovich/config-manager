package com.project.configmanager.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.configmanager.model.device.DeviceInfo;
import com.project.configmanager.model.enums.ConfigType;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "config_versions")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ConfigVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private DeviceInfo device;

    @Column(name = "config_type", nullable = false)
    private int configTypeCode;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt = LocalDateTime.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_config", columnDefinition = "JSONB")
    private String oldConfig;

    @Column(name = "new_config", nullable = false)
    private String newConfig;

    @Column(name = "diff_hash", length = 64)
    private String diffHash;

    @Column(name = "rollback_available")
    private Boolean rollbackAvailable = false;

    public ConfigType getConfigType() {
        return ConfigType.fromCode(this.configTypeCode);
    }

    public void setConfigType(ConfigType type) {
        this.configTypeCode = type.getCode();
    }

    public String getNewConfig() {
        return newConfig;
    }
}
