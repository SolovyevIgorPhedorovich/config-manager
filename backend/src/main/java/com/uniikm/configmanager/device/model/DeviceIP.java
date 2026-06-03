package com.uniikm.configmanager.device.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "device_ip",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"device_info_id", "ip"})
        },
        indexes = {
                @Index(name = "idx_device_ip_ip", columnList = "ip")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceIP {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_info_id", nullable = false)
    private DeviceInfo device;

    @Column(name = "ip", length = 45, nullable = false)
    private String ip;

    @Column(name = "if_name", length = 100)
    private String ifName;

    @Column(name = "is_primary")
    private Boolean isPrimary = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}