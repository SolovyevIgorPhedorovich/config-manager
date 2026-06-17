package com.uniikm.configmanager.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.uniikm.configmanager.auth.service.JwtService;
import com.uniikm.configmanager.auth.utils.CustomUserDetails;
import com.uniikm.configmanager.cache.TokenBlacklistStore;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TokenBlacklistStore blacklist;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        String method = request.getMethod();
        return "OPTIONS".equalsIgnoreCase(method)
                || path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/refresh")
                || path.startsWith("/ws/")
                || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        filterChain.doFilter(request, response);
        return;
    }
    try {
        String token = authHeader.substring(7);
        String username = jwtService.extractUsername(token);
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            // Токен валиден И не отозван (logout / ротация refresh)
            if (jwtService.isTokenValid(token) && !blacklist.isBlacklisted(jwtService.extractJti(token))) {
                // Нормализуем префикс ROLE_ (как в CustomUserDetails) — иначе hasRole(...)
                // не совпадёт со старыми токенами, где роль без префикса.
                List<SimpleGrantedAuthority> authorities = jwtService.extractRoles(token).stream()
                    .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
                // Principal — CustomUserDetails (а не голая строка), чтобы аудит мог
                // получить userId через SecurityFacade.currentUserId().
                CustomUserDetails principal =
                    new CustomUserDetails(jwtService.extractUserId(token), username, authorities);
                UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
    } catch (Exception e) {
        logger.error("JWT authentication failed: " + e.getMessage(), e);
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Invalid or expired token\"}");
        return;   // ← stop the chain, don't forward to /error
    }
    filterChain.doFilter(request, response);
    }
}