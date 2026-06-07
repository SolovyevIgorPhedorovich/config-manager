package com.uniikm.configmanager.auth.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uniikm.configmanager.auth.dto.UserRequest;
import com.uniikm.configmanager.auth.dto.UserResponse;
import com.uniikm.configmanager.auth.model.Role;
import com.uniikm.configmanager.auth.model.User;
import com.uniikm.configmanager.auth.repository.RoleRepository;
import com.uniikm.configmanager.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String DEFAULT_ROLE = "USER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public List<UserResponse> getAll() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    public List<String> getRoleNames() {
        return roleRepository.findAll().stream().map(Role::getName).sorted().toList();
    }

    @Transactional
    public UserResponse create(UserRequest req) {
        if (req.username() == null || req.username().isBlank()) {
            throw new IllegalArgumentException("Имя пользователя обязательно");
        }
        if (req.password() == null || req.password().isBlank()) {
            throw new IllegalArgumentException("Пароль обязателен");
        }
        if (userRepository.findByUsername(req.username()).isPresent()) {
            throw new IllegalArgumentException("Пользователь '" + req.username() + "' уже существует");
        }

        User user = new User();
        user.setUsername(req.username().trim());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setEmail(req.email());
        user.setEnabled(req.enabled() == null || req.enabled());
        user.setRoles(resolveRoles(req.roles()));

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UserRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + id));
        if (req.email() != null) user.setEmail(req.email());
        if (req.enabled() != null) user.setEnabled(req.enabled());
        if (req.roles() != null) user.setRoles(resolveRoles(req.roles()));
        // Пустой пароль = не менять
        if (req.password() != null && !req.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(req.password()));
        }
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void delete(Long id) {
        userRepository.deleteById(id);
    }

    /** Находит роли по имени, недостающие создаёт. По умолчанию — роль USER. */
    private Set<Role> resolveRoles(List<String> names) {
        Set<Role> roles = new HashSet<>();
        List<String> wanted = (names == null || names.isEmpty()) ? List.of(DEFAULT_ROLE) : names;
        for (String name : wanted) {
            if (name == null || name.isBlank()) continue;
            String roleName = name.trim();
            Role role = roleRepository.findByName(roleName)
                    .orElseGet(() -> roleRepository.save(Role.builder().name(roleName).build()));
            roles.add(role);
        }
        return roles;
    }

    private UserResponse toResponse(User user) {
        List<String> roles = user.getRoles() == null ? List.of()
                : user.getRoles().stream().map(Role::getName).sorted().collect(Collectors.toList());
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                Boolean.TRUE.equals(user.getEnabled()),
                roles,
                user.getCreatedAt());
    }
}
