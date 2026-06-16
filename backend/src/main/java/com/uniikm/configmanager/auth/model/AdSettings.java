package com.uniikm.configmanager.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Настройки доменной аутентификации (AD/LDAP), редактируемые из интерфейса.
 * Хранится единственная строка (id = 1).
 */
@Entity
@Table(name = "ad_settings")
@Data
public class AdSettings {

    @Id
    private Long id = 1L;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(nullable = false)
    private String url = "ldap://dc.company.local:389";

    @Column(name = "base_dn", nullable = false)
    private String baseDn = "DC=company,DC=local";

    /** Сервисная (bind) учётка для поиска пользователей; может быть пустой. */
    @Column(name = "user_dn")
    private String userDn;

    /** Пароль bind-учётки (хранится в БД; шифрование — направление развития). */
    @Column(name = "password")
    @ToString.Exclude
    private String password;

    @Column(name = "user_search_filter", nullable = false)
    private String userSearchFilter = "(sAMAccountName={0})";

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
