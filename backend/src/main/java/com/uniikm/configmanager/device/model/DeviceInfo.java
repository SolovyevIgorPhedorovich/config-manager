package com.uniikm.configmanager.device.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import com.uniikm.configmanager.device.enums.DeviceType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "device_info",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "hostname")
        },
        indexes = {
                @Index(name = "idx_device_info_group", columnList = "group_id"),
                @Index(name = "idx_device_info_os_version", columnList = "os_version_id"),
                @Index(name = "idx_devices_type", columnList = "type"),
                @Index(name = "idx_device_operating_system", columnList = "operating_system"),
                @Index(name = "idx_device_manufacturer", columnList = "manufacturer"),
                @Index(name = "idx_device_model", columnList = "model")
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
        return DeviceType.fromCode( this.typeCode );
    }

    public void setType(DeviceType type) {
        this.typeCode = type.getCode();
    }

    public String getOperatingSystem() {
        if (osVersion == null || osVersion.getName() == null) return null;
        String lower = osVersion.getName().toLowerCase();
        if (lower.contains("windows")) return "windows";
        // Linux и распространённые дистрибутивы → "linux" (иначе фронт не опознаёт ОС)
        if (lower.contains("linux") || lower.contains("ubuntu") || lower.contains("debian")
                || lower.contains("centos") || lower.contains("red hat") || lower.contains("redhat")
                || lower.contains("fedora") || lower.contains("proxmox") || lower.contains("alma")
                || lower.contains("rocky") || lower.contains("suse") || lower.contains("astra")
                || lower.contains("alt ") || lower.contains("arch ")) {
            return "linux";
        }
        return lower;
    }

    /** ПК без явно указанной ОС исторически считается Windows. */
    public boolean isWindows() {
        String os = getOperatingSystem();
        return os == null || "windows".equals(os);
    }

    public void addIp(DeviceIP ip) {
        this.ips.add(ip);
        ip.setDevice(this);
    }

    public String getOsVersionString() {
        return osVersion != null
                ? osVersion.getName()
                : "Unknown";
    }

    public String getGroupName() {
        return group != null ? group.getName() : "default";
    }

    public String getGroupDescription() {
        return group != null ? group.getDescription() : "";
    }
}