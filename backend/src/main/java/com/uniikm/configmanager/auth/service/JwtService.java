package com.uniikm.configmanager.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import com.uniikm.configmanager.auth.utils.CustomUserDetails;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    /** Тип токена (claim "type"). */
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final Key signingKey;
    private final long accessExpirationMinutes;
    private final long refreshExpirationDays;

    public JwtService(
            @Value("${security.jwt.secret:Zm9yLWRldi1vbmx5LWRvLW5vdC11c2UtaW4tcHJvZHVjdGlvbi1jaGFuZ2UtbWU=}") String secret,
            @Value("${security.jwt.access-expiration-minutes:30}") long accessExpirationMinutes,
            @Value("${security.jwt.refresh-expiration-days:7}") long refreshExpirationDays) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessExpirationMinutes = accessExpirationMinutes;
        this.refreshExpirationDays = refreshExpirationDays;
    }

    // ------------------------------------------------------------------
    //  Генерация токенов
    // ------------------------------------------------------------------

    /** Access-токен короткого срока действия из объекта аутентификации. */
    public String generateAccessToken(Authentication authentication) {
        CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();
        List<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toList());
        return generateAccessToken(user.getUsername(), user.getId(), roles);
    }

    /** Access-токен из явных данных (используется при обновлении по refresh-токену). */
    public String generateAccessToken(String username, Long userId, List<String> roles) {
        return buildToken(username, userId, roles, TYPE_ACCESS,
                Instant.now().plus(accessExpirationMinutes, ChronoUnit.MINUTES));
    }

    /** Refresh-токен длительного срока действия. */
    public String generateRefreshToken(Authentication authentication) {
        CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();
        List<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toList());
        return generateRefreshToken(user.getUsername(), user.getId(), roles);
    }

    public String generateRefreshToken(String username, Long userId, List<String> roles) {
        return buildToken(username, userId, roles, TYPE_REFRESH,
                Instant.now().plus(refreshExpirationDays, ChronoUnit.DAYS));
    }

    /** Совместимость: старое имя метода (access-токен). */
    public String generateToken(Authentication authentication) {
        return generateAccessToken(authentication);
    }

    private String buildToken(String username, Long userId, List<String> roles,
                              String type, Instant expiry) {
        Instant now = Instant.now();
        return Jwts.builder()
            .id(UUID.randomUUID().toString())     // jti — для чёрного списка
            .subject(username)
            .claim("userId", userId)
            .claim("roles", roles)
            .claim("type", type)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(signingKey)
            .compact();
    }

    // ------------------------------------------------------------------
    //  Извлечение данных
    // ------------------------------------------------------------------

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        Object v = parseClaims(token).get("userId");
        return v == null ? null : Long.valueOf(v.toString());
    }

    public String extractJti(String token) {
        return parseClaims(token).getId();
    }

    public String extractType(String token) {
        Object t = parseClaims(token).get("type");
        return t == null ? null : t.toString();
    }

    public Instant extractExpiration(String token) {
        return parseClaims(token).getExpiration().toInstant();
    }

    public List<String> extractRoles(String token) {
        Claims claims = parseClaims(token);
        Object rolesObj = claims.get("roles");
        if (rolesObj instanceof List<?>) {
            return ((List<?>) rolesObj).stream()
                .map(Object::toString)
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public Collection<? extends GrantedAuthority> getAuthorities(String token) {
        return extractRoles(token).stream()
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    //  Валидация
    // ------------------------------------------------------------------

    public boolean isValid(String token) {
        try {
            return parseClaims(token).getExpiration().after(new Date());
        } catch (Exception ex) {
            return false;
        }
    }

    /** Валиден и относится к ожидаемому типу (access/refresh). */
    public boolean isValid(String token, String expectedType) {
        try {
            Claims claims = parseClaims(token);
            return claims.getExpiration().after(new Date())
                    && expectedType.equals(claims.get("type"));
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean isTokenValid(String token) {
        return isValid(token);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith((SecretKey) signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
