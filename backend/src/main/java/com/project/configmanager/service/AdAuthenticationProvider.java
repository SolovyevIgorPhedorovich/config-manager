package com.project.configmanager.service;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ldap.AuthenticationException;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.AbstractContextMapper;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import javax.naming.Context;
import javax.naming.directory.DirContext;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Data
public class AdAuthenticationProvider implements org.springframework.security.authentication.AuthenticationProvider {

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

        try {
            UserDetails localUser = userDetailsService.loadUserByUsername(username);
            if (localUser != null && passwordEncoder().matches(password, localUser.getPassword())) {
                return new UsernamePasswordAuthenticationToken(
                    localUser,
                    password,
                    localUser.getAuthorities()
                );
            }
        } catch (UsernameNotFoundException ignored) {}

/*/        if (adEnabled && ldapTemplate != null) {
            try {
                LdapQuery query = LdapQueryBuilder.query()
                    .base(baseDn)
                    .filter(userSearchFilter.replace("{0}", username));
                    
               DirContextOperations ctx = ldapTemplate.authenticate(query, password);

                String userDn = ctx.getNameInNamespace();
                List<String> groups = getGroupsForUser(username);
                var authorities = groups.stream()
                    .map(g -> new SimpleGrantedAuthority("ROLE_" + g.toUpperCase()))
                    .collect(Collectors.toList());

                UserDetails adUser = new org.springframework.security.core.userdetails.User(
                    username, password,
                    true, true, true, true,
                    authorities
                );

                return new UsernamePasswordAuthenticationToken(adUser, password, authorities);

            } catch (AuthenticationException e) {
                throw new BadCredentialsException("AD Authentication failed");
            }
        }*/

        throw new BadCredentialsException("Invalid credentials");
    }

    private List<String> getGroupsForUser(String username) {
        return Collections.emptyList(); 
    }

    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }
}