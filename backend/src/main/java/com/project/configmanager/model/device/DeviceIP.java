package com.project.configmanager.model.device;

import jakarta.persistence.*;
import lombok.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.project.configmanager.model.enums.DeviceType;

@Entity
@Table(name = "device_ip")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class DeviceIP {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_info_id", nullable = false)
    private DeviceInfo device;

    @Column(name = "ip", length = 45, nullable = false)
    private String ip;

    @Column(name = "if_name", length = 100)
    private String ifName;

    @Column(name = "is_primary")
    private Boolean isPrimary;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public DeviceIP(String ip) {
        this.ip = ip;
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    public static DeviceIP fromString(String ip) {
        return new DeviceIP(ip);
    }

    @JsonCreator
    public static DeviceIP fromIp(@JsonProperty("ip") String ip) {
        return new DeviceIP(ip);
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

}