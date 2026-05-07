package com.project.configmanager.controller;


import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @PostMapping("/login")
    public String login() {
        return "auth-success";
    }

    @GetMapping("/user")
    public String getUser(@RequestHeader("Authorization") String authHeader) {
        return "admin";
    }
}
