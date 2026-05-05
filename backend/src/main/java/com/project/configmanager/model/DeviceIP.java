package com.project.configmanager.model;

import jakarta.persistence.*;
import lombok.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.project.configmanager.model.enums.DeviceType;

@Entity
@Table(name = "devices_ip")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class DeviceIP {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch =  FetchType.LAZY)
    @JoinColumn(name="device_id", nullable = false)
    private Device device;

    @Column(name = "ip", length = 45, nullable = false, columnDefinition = "INET")
    @Type(InetAddress.class)
    private InetAddress ip;

    @Column(name = "if_name", length = 100)
    private String ifName;

    @Column(name = "is_primary", columnDefinition = "DEFAULT false")
    private Boolean isPrimary;

    public String getIpAsString() {
        return ip == null ? null : ip.getHostAddress();
    }

    public void setIp(InetAddress ip) { this.ip = ip; }

    public void setIp(String ip) {
        if (ip == null) this.ip = null;
        else
        try {this.ip = InetAddress.getByName(ip); }
        catch (UnknownHostException e) { throw new IllegalArgumentException(e); }
    }

    public String getIpString() {
        return this.ip.getHostName();
    }
}