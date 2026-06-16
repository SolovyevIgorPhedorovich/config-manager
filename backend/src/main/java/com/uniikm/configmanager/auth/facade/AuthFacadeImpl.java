package com.uniikm.configmanager.auth.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.uniikm.configmanager.audit.enums.AuditAction;
import com.uniikm.configmanager.auth.dto.LoginRequest;
import com.uniikm.configmanager.auth.dto.LoginResponse;
import com.uniikm.configmanager.auth.dto.mapper.AuthMapper;
import com.uniikm.configmanager.auth.event.AuthEvent;
import com.uniikm.configmanager.auth.model.User;
import com.uniikm.configmanager.auth.repository.UserRepository;
import com.uniikm.configmanager.auth.service.JwtService;
import com.uniikm.configmanager.auth.service.TokenBlacklistService;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthFacadeImpl implements AuthFacade {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuthMapper authMapper;
    private final TokenBlacklistService blacklist;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;

    @Override
    public LoginResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            String accessToken = jwtService.generateAccessToken(authentication);
            String refreshToken = jwtService.generateRefreshToken(authentication);

            // Публикуем событие успешного входа
            eventPublisher.publishEvent(new AuthEvent(
                AuditAction.LOGIN_SUCCESS,
                authentication.getName(),
                authentication.getName(),
                null,
                null
            ));

            return authMapper.toResponse(accessToken, refreshToken, authentication);
        } catch (BadCredentialsException ex) {
            log.warn("Failed login attempt for user: {}", request.getUsername());
            // Публикуем событие неудачного входа
            eventPublisher.publishEvent(new AuthEvent(
                AuditAction.LOGIN_FAILURE,
                request.getUsername(),
                request.getUsername(),
                ex.getMessage(),
                null
            ));
            throw ex;
        }
    }

    @Override
    public LoginResponse refresh(String refreshToken) {
        if (refreshToken == null
                || !jwtService.isValid(refreshToken, JwtService.TYPE_REFRESH)
                || blacklist.isBlacklisted(jwtService.extractJti(refreshToken))) {
            throw new BadCredentialsException("Недействительный refresh-токен");
        }
        String username = jwtService.extractUsername(refreshToken);
        Long userId = jwtService.extractUserId(refreshToken);
        java.util.List<String> roles = jwtService.extractRoles(refreshToken);

        // Ротация: старый refresh-токен отзываем, выдаём новую пару
        blacklist.blacklist(jwtService.extractJti(refreshToken),
                            jwtService.extractExpiration(refreshToken));

        String newAccess = jwtService.generateAccessToken(username, userId, roles);
        String newRefresh = jwtService.generateRefreshToken(username, userId, roles);
        return new LoginResponse(newAccess, newRefresh, username);
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        revoke(accessToken);
        revoke(refreshToken);
    }

    private void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            blacklist.blacklist(jwtService.extractJti(token), jwtService.extractExpiration(token));
        } catch (Exception ex) {
            // повреждённый/просроченный токен отзывать не нужно
            log.debug("Не удалось отозвать токен: {}", ex.getMessage());
        }
    }

    @Override
    public String getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Unauthorized");
        }
        return authentication.getName();
    }

    @Override
    public Map<Long, String> getUsernames(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        java.util.List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
    }
}