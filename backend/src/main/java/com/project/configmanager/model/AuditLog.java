package com.project.configmanager.model;

import com.project.configmanager.model.enums.AuditAction;
import com.project.configmanager.model.enums.TaskStatus;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_log")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String userId;

    @Enumerated(EnumType.STRING) 
    @Column(name = "action_type", nullable = false, length = 50)
    private AuditAction actionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_device_id")
    private Device targetDevice;

    @Column(name = "old_config", columnDefinition = "TEXT")
    private String oldConfig;

    @Column(name = "new_config", columnDefinition = "TEXT")
    private String newConfig;

    @Column(name = "status", nullable = false)
    private int statusValue;

    @Column(name = "target_ip", length = 50)
    private String targetIp;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    private Object details;
    
    public TaskStatus getStatus() {
        return TaskStatus.fromCode(this.statusValue);
    }

    public void setStatus(TaskStatus status) {
        this.statusValue = status.getCode();
    }
}