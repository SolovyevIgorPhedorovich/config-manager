package com.uniikm.configmanager.auth.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uniikm.configmanager.auth.dto.AdSettingsRequest;
import com.uniikm.configmanager.auth.dto.AdSettingsResponse;
import com.uniikm.configmanager.auth.model.AdSettings;
import com.uniikm.configmanager.auth.service.AdSettingsService;

import lombok.RequiredArgsConstructor;

/**
 * Управление настройками доменной аутентификации (AD/LDAP) из интерфейса.
 * Доступ — только роль ADMIN (см. SecurityConfig: /api/v1/settings/**).
 */
@RestController
@RequestMapping("/api/v1/settings/ad")
@RequiredArgsConstructor
public class AdSettingsController {

    private final AdSettingsService service;

    /** Текущие настройки AD (без пароля). */
    @GetMapping
    public AdSettingsResponse get() {
        return service.toResponse(service.current());
    }

    /** Сохранить настройки AD. */
    @PutMapping
    public AdSettingsResponse update(@RequestBody AdSettingsRequest request) {
        return service.update(request);
    }

    /**
     * Проверка подключения. Если в теле переданы параметры — проверяются они
     * (с подстановкой текущего пароля при пустом поле), иначе — сохранённые.
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> test(@RequestBody(required = false) AdSettingsRequest request) {
        AdSettings probe = service.current();
        if (request != null) {
            if (request.url() != null && !request.url().isBlank())     probe.setUrl(request.url().trim());
            if (request.baseDn() != null && !request.baseDn().isBlank()) probe.setBaseDn(request.baseDn().trim());
            if (request.userDn() != null)  probe.setUserDn(request.userDn().isBlank() ? null : request.userDn().trim());
            if (request.password() != null && !request.password().isBlank()) probe.setPassword(request.password());
        }
        String error = service.testConnection(probe);
        return ResponseEntity.ok(error == null
                ? Map.of("success", true, "message", "Подключение установлено")
                : Map.of("success", false, "error", error));
    }
}
