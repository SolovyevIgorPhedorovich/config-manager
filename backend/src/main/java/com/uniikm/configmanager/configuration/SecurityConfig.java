package com.uniikm.configmanager.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.uniikm.configmanager.auth.service.AdAuthenticationProvider;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AdAuthenticationProvider adAuthenticationProvider;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        authBuilder.authenticationProvider(adAuthenticationProvider);
        return authBuilder.build();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/api/v1/auth/login").permitAll()   // логин открыт
                .requestMatchers("/api/v1/auth/refresh").permitAll() // обновление токена по refresh
                .requestMatchers("/api/v1/auth/user").permitAll()    // проверка токена
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/actuator/health", "/api/actuator/health/**").permitAll()  // health открыт для мониторинга

                // ── Контроль доступа по ролям (RBAC) ──────────────────────────────
                // ADMIN    — всё, включая управление пользователями/ролями
                // OPERATOR — управление устройствами/конфигами/сканом/терминалом
                // VIEWER   — только чтение (GET)
                .requestMatchers("/api/v1/users/**").hasRole("ADMIN")                        // пользователи и роли
                .requestMatchers("/api/v1/settings/**").hasRole("ADMIN")                      // системные настройки (AD и др.)
                .requestMatchers(HttpMethod.GET, "/api/v1/devices/scan", "/api/v1/devices/scan/**")
                    .hasAnyRole("ADMIN", "OPERATOR")                                          // скан пишет устройства
                .requestMatchers("/api/v1/devices/*/terminal/**").hasAnyRole("ADMIN", "OPERATOR") // терминал
                .requestMatchers("/api/commands/**").hasAnyRole("ADMIN", "OPERATOR")          // удалённые команды
                .requestMatchers("/api/v1/devices/configure").hasAnyRole("ADMIN", "OPERATOR") // применение конфига
                .requestMatchers(HttpMethod.POST, "/api/v1/config/**", "/api/v1/templates/**")
                    .hasAnyRole("ADMIN", "OPERATOR")                                          // сравнение/создание конфигов и шаблонов
                .requestMatchers(HttpMethod.POST,   "/api/v1/devices/**").hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers(HttpMethod.PUT,    "/api/v1/devices/**").hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/devices/**").hasAnyRole("ADMIN", "OPERATOR")

                .anyRequest().authenticated()  // остальное (чтение) — любой аутентифицированный
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:5174",
                "http://localhost:5173",
                "http://localhost:4173",
                "http://localhost:4174"
        ));
        config.setAllowCredentials(true);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Content-Type", "X-Auth-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}