package com.project.configmanager.auth.service;

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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;

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
            LdapQuery query = LdapQueryBuilder.query()
                .base(baseDn)
                .filter(userSearchFilter.replace("{0}", username));

            ldapTemplate.authenticate(query, password);

            UserDetails adUser = new org.springframework.security.core.userdetails.User(
                username,
                password,
                Collections.emptyList()
            );

            return new UsernamePasswordAuthenticationToken(adUser, password, adUser.getAuthorities());
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