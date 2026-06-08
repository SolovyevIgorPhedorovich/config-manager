package com.uniikm.configmanager.configuration;

import javax.naming.directory.DirContext;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Проверка доступности домена (Active Directory / LDAP) для /actuator/health.
 *
 * Заменяет штатный {@code LdapHealthIndicator} Spring Boot, который пытается
 * подключиться к LDAP всегда — даже когда доменная аутентификация выключена
 * ({@code ad.enabled=false}) — и роняет общий статус в DOWN на плейсхолдере
 * dc.company.local. Здесь:
 *   - AD выключен  → UP, деталь "disabled" (подключение не проверяется);
 *   - AD включён   → реальная проверка коннекта к контроллеру домена.
 *
 * Компонент называется "domain" → появляется в health под components.domain.
 */
@Slf4j
@Component("domain")
public class DomainHealthIndicator implements HealthIndicator {

    private final LdapContextSource contextSource;

    @Value("${ad.enabled:false}")
    private boolean adEnabled;

    @Value("${ad.url:ldap://dc.company.local:389}")
    private String url;

    @Value("${ad.base:DC=company,DC=local}")
    private String baseDn;

    public DomainHealthIndicator(LdapContextSource contextSource) {
        this.contextSource = contextSource;
    }

    @Override
    public Health health() {
        if (!adEnabled) {
            // Домен не используется — это не ошибка, не валим health.
            return Health.up()
                    .withDetail("ad", "disabled")
                    .withDetail("hint", "ad.enabled=false — доменная аутентификация выключена, используется локальная БД")
                    .build();
        }

        DirContext ctx = null;
        try {
            ctx = contextSource.getReadOnlyContext(); // реальный коннект к DC (с таймаутами)
            return Health.up()
                    .withDetail("ad", "enabled")
                    .withDetail("url", url)
                    .withDetail("baseDn", baseDn)
                    .withDetail("connection", "established")
                    .build();
        } catch (Exception e) {
            log.warn("Проверка домена не прошла ({}): {}", url, rootMessage(e));
            return Health.down()
                    .withDetail("ad", "enabled")
                    .withDetail("url", url)
                    .withDetail("baseDn", baseDn)
                    .withDetail("error", rootMessage(e))
                    .build();
        } finally {
            if (ctx != null) {
                try { ctx.close(); } catch (Exception ignored) {}
            }
        }
    }

    private String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        return (msg == null || msg.isBlank())
                ? root.getClass().getSimpleName()
                : root.getClass().getSimpleName() + ": " + msg;
    }
}
