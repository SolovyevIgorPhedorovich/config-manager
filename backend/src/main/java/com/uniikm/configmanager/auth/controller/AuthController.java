package com.uniikm.configmanager.auth.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.uniikm.configmanager.auth.dto.LoginRequest;
import com.uniikm.configmanager.auth.dto.LoginResponse;
import com.uniikm.configmanager.auth.dto.RefreshRequest;
import com.uniikm.configmanager.auth.facade.AuthFacade;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AuthController {

    private final AuthFacade authFacade;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        // BadCredentialsException обрабатывается в GlobalExceptionHandler:
        // отдаётся 401 с кратким сообщением в теле для интерфейса.
        return ResponseEntity.ok(authFacade.login(request));
    }

    /** Обновление access-токена по refresh-токену (с ротацией refresh). */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authFacade.refresh(request.refreshToken()));
    }

    /** Выход: отзыв access- и refresh-токенов (чёрный список). */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) RefreshRequest request) {
        String accessToken = (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7) : null;
        String refreshToken = request != null ? request.refreshToken() : null;
        authFacade.logout(accessToken, refreshToken);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/user")
    public ResponseEntity<String> getUser() {
        try {
            String username = authFacade.getCurrentUser();
            return ResponseEntity.ok(username);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
    }
}