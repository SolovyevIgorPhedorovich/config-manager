package com.uniikm.configmanager.auth.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import javax.naming.directory.DirContext;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uniikm.configmanager.auth.dto.AdSettingsRequest;
import com.uniikm.configmanager.auth.dto.AdSettingsResponse;
import com.uniikm.configmanager.auth.model.AdSettings;
import com.uniikm.configmanager.auth.repository.AdSettingsRepository;

import lombok.RequiredArgsConstructor;

/**
 * Хранение и применение настроек доменной аутентификации (AD/LDAP).
 * Настройки лежат в БД (одна строка) и редактируются из интерфейса; при первом
 * обращении строка создаётся из значений по умолчанию (свойства ad.* / env).
 */
@Service
@RequiredArgsConstructor
public class AdSettingsService {

    private final AdSettingsRepository repository;

    @Value("${ad.enabled:false}")          private boolean defEnabled;
    @Value("${ad.url:ldap://dc.company.local:389}") private String defUrl;
    @Value("${ad.base:DC=company,DC=local}") private String defBase;
    @Value("${ad.userDn:}")                 private String defUserDn;
    @Value("${ad.password:}")               private String defPassword;
    @Value("${ad.userSearchFilter:(sAMAccountName={0})}") private String defFilter;

    /** Текущие настройки; при отсутствии — создаются из значений по умолчанию. */
    @Transactional
    public AdSettings current() {
        return repository.findById(1L).orElseGet(() -> {
            AdSettings s = new AdSettings();
            s.setId(1L);
            s.setEnabled(defEnabled);
            s.setUrl(defUrl);
            s.setBaseDn(defBase);
            s.setUserDn(defUserDn == null || defUserDn.isBlank() ? null : defUserDn);
            s.setPassword(defPassword == null || defPassword.isBlank() ? null : defPassword);
            s.setUserSearchFilter(defFilter);
            s.setUpdatedAt(LocalDateTime.now());
            return repository.save(s);
        });
    }

    @Transactional
    public AdSettingsResponse update(AdSettingsRequest req) {
        AdSettings s = current();
        if (req.enabled() != null)       s.setEnabled(req.enabled());
        if (req.url() != null)           s.setUrl(req.url().trim());
        if (req.baseDn() != null)        s.setBaseDn(req.baseDn().trim());
        // userDn: пустая строка очищает (анонимный/неуказанный bind)
        if (req.userDn() != null)        s.setUserDn(req.userDn().isBlank() ? null : req.userDn().trim());
        // password: пустой — оставляем прежний; иначе обновляем
        if (req.password() != null && !req.password().isBlank()) s.setPassword(req.password());
        if (req.userSearchFilter() != null && !req.userSearchFilter().isBlank())
            s.setUserSearchFilter(req.userSearchFilter().trim());
        s.setUpdatedAt(LocalDateTime.now());
        return toResponse(repository.save(s));
    }

    public AdSettingsResponse toResponse(AdSettings s) {
        return new AdSettingsResponse(
                s.isEnabled(), s.getUrl(), s.getBaseDn(), s.getUserDn(),
                s.getPassword() != null && !s.getPassword().isBlank(),
                s.getUserSearchFilter());
    }

    // ------------------------------------------------------------------
    //  Построение LDAP-клиента и проверка подключения
    // ------------------------------------------------------------------

    /** LdapTemplate под текущие настройки (готов к поиску в AD). */
    public LdapTemplate ldapTemplate(AdSettings s) {
        LdapTemplate template = new LdapTemplate(contextSource(s));
        // AD от корня домена возвращает continuation references — игнорируем,
        // иначе поиск пользователя падает с PartialResultException.
        template.setIgnorePartialResultException(true);
        return template;
    }

    /** Проверка подключения к контроллеру домена; null — успех, иначе текст ошибки. */
    public String testConnection(AdSettings s) {
        DirContext ctx = null;
        try {
            ctx = contextSource(s).getReadOnlyContext();
            return null;
        } catch (Exception e) {
            return rootMessage(e);
        } finally {
            if (ctx != null) {
                try { ctx.close(); } catch (Exception ignored) {}
            }
        }
    }

    private LdapContextSource contextSource(AdSettings s) {
        LdapContextSource cs = new LdapContextSource();
        cs.setUrl(s.getUrl());
        cs.setBase(s.getBaseDn());
        if (s.getUserDn() != null && !s.getUserDn().isBlank()) {
            cs.setUserDn(s.getUserDn());
        }
        if (s.getPassword() != null && !s.getPassword().isBlank()) {
            cs.setPassword(s.getPassword());
        }
        Map<String, Object> env = new HashMap<>();
        env.put("com.sun.jndi.ldap.connect.timeout", "3000");
        env.put("com.sun.jndi.ldap.read.timeout", "5000");
        cs.setBaseEnvironmentProperties(env);
        // ignore: AD возвращает referral на DNS-имя домена, которое может не
        // резолвиться с хоста приложения (UnknownHostException).
        cs.setReferral("ignore");
        cs.afterPropertiesSet();
        return cs;
    }

    public static String rootMessage(Throwable t) {
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
