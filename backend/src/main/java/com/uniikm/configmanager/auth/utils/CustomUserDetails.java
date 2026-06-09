package com.uniikm.configmanager.auth.utils;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.uniikm.configmanager.auth.model.User;

public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        // Authorities из реальных ролей пользователя (раньше был хардкод ROLE_ADMIN,
        // из-за чего ЛЮБОЙ пользователь получал полный доступ). Нормализуем префикс
        // ROLE_, чтобы hasRole("ADMIN") совпадал независимо от формата имени в БД.
        this.authorities = (user.getRoles() == null) ? List.of()
                : user.getRoles().stream()
                    .map(r -> r.getName().startsWith("ROLE_") ? r.getName() : "ROLE_" + r.getName())
                    .distinct()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
    }

    /**
     * Конструктор для внешних (доменных, AD) пользователей: пароль в системе
     * не хранится (проверяется доменом), роли передаются явно.
     */
    public CustomUserDetails(Long id, String username,
                             Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.password = null;
        this.authorities = (authorities == null) ? List.of() : authorities;
    }

    public Long getId() {
        return id;
    }

    @Override public String getUsername() { return username; }
    @Override public String getPassword() { return password; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
