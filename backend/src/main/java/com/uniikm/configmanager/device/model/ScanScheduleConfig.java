package com.uniikm.configmanager.device.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "scan_schedules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanScheduleConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 50)
    private String subnet;

    @Column(nullable = false)
    @Builder.Default
    private int mask = 24;

    @Column(nullable = false)
    @Builder.Default
    private int port = 161;

    @Column(nullable = false, length = 100)
    @Builder.Default
    private String community = "public";

    @Column(name = "snmp_version", nullable = false, length = 10)
    @Builder.Default
    private String snmpVersion = "v2c";

    @Column(name = "scan_mode", nullable = false, length = 50)
    @Builder.Default
    private String scanMode = "all";

    // Spring 6-field cron: "sec min hour dom month dow", e.g. "0 0 2 * * *"
    @Column(name = "cron_expression", length = 100)
    private String cronExpression;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "ssh_username", length = 255)
    private String sshUsername;

    @Column(name = "ssh_password", length = 255)
    private String sshPassword;

    @Column(name = "winrm_username", length = 255)
    private String winrmUsername;

    @Column(name = "winrm_password", length = 255)
    private String winrmPassword;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "last_run_status", length = 50)
    private String lastRunStatus;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
