package com.project.configmanager.auth.facade;

import com.project.configmanager.audit.enums.AuditAction;
import com.project.configmanager.auth.dto.LoginRequest;
import com.project.configmanager.auth.dto.LoginResponse;
import com.project.configmanager.auth.dto.mapper.AuthMapper;
import com.project.configmanager.auth.events.AuthEvent;
import com.project.configmanager.auth.service.JwtService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthFacadeImpl implements AuthFacade {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuthMapper authMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public LoginResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            String token = jwtService.generateToken(authentication);

            // Публикуем событие успешного входа
            eventPublisher.publishEvent(new AuthEvent(
                AuditAction.LOGIN_SUCCESS,
                authentication.getName(),
                authentication.getName(),
                null,
                null
            ));

            return authMapper.toResponse(token, authentication);
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
    public String getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Unauthorized");
        }
        return authentication.getName();
    }
}