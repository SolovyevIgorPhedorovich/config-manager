package com.uniikm.configmanager.auth.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.uniikm.configmanager.auth.dto.LoginRequest;
import com.uniikm.configmanager.auth.dto.LoginResponse;
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