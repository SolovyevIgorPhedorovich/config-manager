package com.uniikm.configmanager.device.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Профиль доступа для сканирования/опроса устройств.
 * Пароли хранятся в зашифрованном виде (см. SecretCipher).
 */
@Entity
@Table(
        name = "scan_credentials",
        uniqueConstraints = @UniqueConstraint(columnNames = "name")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "domain", length = 255)
    private String domain;   // AD-домен; если задан, WinRM подключается как DOMAIN\\user

    @Column(name = "ssh_username", length = 255)
    private String sshUsername;

    @Column(name = "ssh_password", length = 512)
    private String sshPassword;       // зашифровано

    @Column(name = "winrm_username", length = 255)
    private String winrmUsername;

    @Column(name = "winrm_password", length = 512)
    private String winrmPassword;     // зашифровано

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
