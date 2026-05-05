package com.project.configmanager.model.device;

import jakarta.persistence.*;
import lombok.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.project.configmanager.model.enums.DeviceType;

@Entity
@Table(name = "device_info")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class DeviceInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String hostname;

    @OneToMany(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DeviceIP> ips = new ArrayList<>();

    @Column(name = "type", nullable = false)
    private Integer typeCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private DeviceGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "os_version_id")
    private DeviceOS osVersion;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public DeviceType getType() {
        return DeviceType.fromCode(this.typeCode);
    }

    public void setType(DeviceType type) {
        this.typeCode = type.getCode();
    }

    public void setIp(DeviceIP ip) {
        this.ips.add(ip);
    }

    public String getOsVersionString() {
        return osVersion != null ? 
            String.format("%s %d.%d", osVersion.getName(), 
                osVersion.getMajorVersion(), osVersion.getMinorVersion()) : "Unknown";
    }

    public String getGroupName() {
        return group != null ? group.getName() : "default";
    }
}