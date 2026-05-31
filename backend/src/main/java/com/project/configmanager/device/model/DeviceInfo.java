package com.project.configmanager.device.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.project.configmanager.device.enums.DeviceType;

@Entity
@Table(
        name = "device_info",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "hostname")
        },
        indexes = {
                @Index(name = "idx_device_info_group", columnList = "group_id"),
                @Index(name = "idx_device_info_os_version", columnList = "os_version_id"),
                @Index(name = "idx_devices_type", columnList = "type")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String hostname;

    @OneToMany(
            mappedBy = "device",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<DeviceIP> ips = new ArrayList<>();

    @Column(name = "type", nullable = false)
    private Integer typeCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private DeviceGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "os_version_id")
    private DeviceOS osVersion;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public DeviceType getType() {
        return DeviceType.fromCode(this.typeCode);
    }

    public void setType(DeviceType type) {
        this.typeCode = type.getCode();
    }

    public void addIp(DeviceIP ip) {
        this.ips.add(ip);
        ip.setDevice(this);
    }

    public String getOsVersionString() {
        return osVersion != null
                ? String.format("%s %d.%d",
                        osVersion.getName(),
                        osVersion.getMajorVersion(),
                        osVersion.getMinorVersion())
                : "Unknown";
    }

    public String getGroupName() {
        return group != null ? group.getName() : "default";
    }

    public String getGroupDescriotion() {
        return group != null ? group.getDescription() : "";
    }

    
}