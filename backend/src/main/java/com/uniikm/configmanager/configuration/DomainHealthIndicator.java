package com.uniikm.configmanager.configuration;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.uniikm.configmanager.auth.model.AdSettings;
import com.uniikm.configmanager.auth.service.AdSettingsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Проверка доступности домена (Active Directory / LDAP) для /actuator/health.
 *
 * Настройки AD берутся из {@link AdSettingsService} (редактируются из интерфейса):
 *   - AD выключен  → UP, деталь "disabled" (подключение не проверяется);
 *   - AD включён   → реальная проверка коннекта к контроллеру домена.
 *
 * Компонент называется "domain" → появляется в health под components.domain.
 */
@Slf4j
@Component("domain")
@RequiredArgsConstructor
public class DomainHealthIndicator implements HealthIndicator {

    private final AdSettingsService adSettingsService;

    @Override
    public Health health() {
        AdSettings s = adSettingsService.current();

        if (!s.isEnabled()) {
            return Health.up()
                    .withDetail("ad", "disabled")
                    .withDetail("hint", "доменная аутентификация выключена, используется локальная БД")
                    .build();
        }

        String error = adSettingsService.testConnection(s);
        if (error == null) {
            return Health.up()
                    .withDetail("ad", "enabled")
                    .withDetail("url", s.getUrl())
                    .withDetail("baseDn", s.getBaseDn())
                    .withDetail("connection", "established")
                    .build();
        }
        log.warn("Проверка домена не прошла ({}): {}", s.getUrl(), error);
        return Health.down()
                .withDetail("ad", "enabled")
                .withDetail("url", s.getUrl())
                .withDetail("baseDn", s.getBaseDn())
                .withDetail("error", error)
                .build();
    }
}
