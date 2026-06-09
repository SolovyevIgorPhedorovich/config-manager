package com.uniikm.configmanager.auth.service;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.uniikm.configmanager.auth.utils.CustomUserDetails;

import java.util.List;

@Component
@Data
public class AdAuthenticationProvider implements AuthenticationProvider {

    @Value("${ad.enabled:false}")
    private boolean adEnabled;

    @Value("${ad.url:ldap://dc.company.local:389}")
    private String url;

    @Value("${ad.base:DC=company,DC=local}")
    private String baseDn;

    @Value("${ad.userSearchFilter:(sAMAccountName={0})}")
    private String userSearchFilter;

    private final LdapTemplate ldapTemplate;
    private final UserDetailsService userDetailsService; // загружает из БД

    public AdAuthenticationProvider(LdapContextSource contextSource, UserDetailsService userDetailsService) {
        this.ldapTemplate = new LdapTemplate(contextSource);
        // AD при поиске от корня домена возвращает continuation references;
        // игнорируем PartialResultException, иначе поиск пользователя падает.
        this.ldapTemplate.setIgnorePartialResultException(true);
        this.userDetailsService = userDetailsService;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String password = (String) authentication.getCredentials();
        String authType = resolveAuthType();

        if ("AD".equalsIgnoreCase(authType) && adEnabled) {
            return authenticateUsingAd(username, password);
        }

        return authenticateUsingDatabase(username, password);
    }

    private Authentication authenticateUsingDatabase(String username, String password) {
        try {
            UserDetails localUser = userDetailsService.loadUserByUsername(username);
            if (localUser != null && passwordEncoder().matches(password, localUser.getPassword())) {
                return new UsernamePasswordAuthenticationToken(
                    localUser,
                    password,
                    localUser.getAuthorities()
                );
            }
        } catch (UsernameNotFoundException ignored) {
            // continue to unified error below
        }

        throw new BadCredentialsException("Invalid credentials");
    }

    private Authentication authenticateUsingAd(String username, String password) {
        try {
            // base НЕ указываем: LdapContextSource уже настроен на baseDn,
            // поиск идёт относительно него (иначе base удваивается -> NO_OBJECT).
            LdapQuery query = LdapQueryBuilder.query()
                .filter(userSearchFilter.replace("{0}", username));

            // Домен подтверждает пароль (bind под учёткой пользователя)
            ldapTemplate.authenticate(query, password);

            // Роли и идентификатор берём из локальной учётной записи, если она
            // заведена в БД; иначе доменному пользователю выдаём роль по умолчанию.
            // Principal — CustomUserDetails (его ожидает JwtService при выпуске токена).
            CustomUserDetails principal;
            try {
                principal = (CustomUserDetails) userDetailsService.loadUserByUsername(username);
            } catch (UsernameNotFoundException notFound) {
                principal = new CustomUserDetails(
                    null, username, List.of(new SimpleGrantedAuthority("ROLE_VIEWER")));
            }

            return new UsernamePasswordAuthenticationToken(
                principal, password, principal.getAuthorities());
        } catch (org.springframework.ldap.AuthenticationException ex) {
            throw new BadCredentialsException("AD Authentication failed");
        }
    }

    private String resolveAuthType() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "DB";
        }

        String authType = attrs.getRequest().getHeader("X-Auth-Type");
        if (authType == null || authType.isBlank()) {
            authType = attrs.getRequest().getParameter("authType");
        }
        return authType == null || authType.isBlank() ? "DB" : authType;
    }


    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }
}