package com.project.configmanager.model;

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
@Table(name = "devices_info")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String hostname;

    @OneToMany(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DeviceIP> ip = new ArrayList<>();

    @Column(name = "type", nullable = false)
    private Integer typeCode;

    @Column(name = "group_name", length = 255, columnDefinition = "DEFAULT 'default'")
    private String groupName;

    @Column(name = "os_version")
    private String osVersion;

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
        this.ip.add(ip);
    }
}