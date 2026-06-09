package com.uniikm.configmanager.config.model;

import com.uniikm.configmanager.config.enums.ScheduledApplyStatus;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Заявка на отложенное применение конфигурации к устройству, которого не было
 * в сети в момент запроса. Учётные данные шифруются {@code SecretCipher},
 * чтобы фоновый планировщик мог применить конфиг без участия оператора, когда
 * устройство появится в сети.
 */
@Entity
@Table(name = "scheduled_config_apply",
        indexes = {
                @Index(name = "idx_sca_status", columnList = "status"),
                @Index(name = "idx_sca_device", columnList = "device_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledConfigApply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "config_version_id", nullable = false)
    private Long configVersionId;

    @Column(name = "username", length = 255)
    private String username;

    /** Зашифрованный пароль (enc:...). */
    @Column(name = "enc_password", length = 512)
    private String encPassword;

    /** Зашифрованная SNMP community (enc:...). */
    @Column(name = "enc_community", length = 512)
    private String encCommunity;

    @Column(name = "port")
    private Integer port;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ScheduledApplyStatus status = ScheduledApplyStatus.PENDING;

    /** Источник заявки: CONFIG (прямое применение) или TEMPLATE. */
    @Column(name = "source", length = 20)
    private String source;

    @Column(name = "batch_id", length = 64)
    private String batchId;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;
}
