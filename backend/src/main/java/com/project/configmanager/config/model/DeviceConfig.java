package com.project.configmanager.config.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

import com.project.configmanager.device.model.DeviceInfo;

@Entity
@Table(name = "device_config",
       uniqueConstraints = @UniqueConstraint(columnNames = "device_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceConfig {

    @Id
    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @OneToOne
    @JoinColumn(name = "config_id", nullable = false)
    private ConfigVersion configVersion;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    @PrePersist
    protected void onCreate() {
        if (appliedAt == null) appliedAt = LocalDateTime.now();
    }
}