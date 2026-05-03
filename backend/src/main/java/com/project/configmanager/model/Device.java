package com.project.configmanager.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import com.project.configmanager.model.enums.DeviceType;

@Entity
@Table(name = "devices")
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

    @Column(name = "ip", nullable = false, columnDefinition = "INET")
    private String ip;

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

    public String getIpAsString() {
        return this.ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }
}