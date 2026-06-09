package com.uniikm.configmanager.error;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты глобального обработчика ошибок: проверяют, что каждое
 * исключение преобразуется в корректный HTTP-статус и краткое тело-сообщение.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/test");
        when(request.getMethod()).thenReturn("GET");
    }

    @Test
    @DisplayName("BadCredentialsException -> 401 с понятным сообщением")
    void badCredentials_returns401() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleAuth(new BadCredentialsException("Invalid credentials"), request);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().message()).isEqualTo("Неверный логин или пароль");
        assertThat(resp.getBody().status()).isEqualTo(401);
        assertThat(resp.getBody().path()).isEqualTo("/api/v1/test");
    }

    @Test
    @DisplayName("AccessDeniedException -> 403")
    void accessDenied_returns403() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleAccessDenied(new AccessDeniedException("denied"), request);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().message()).contains("Недостаточно прав");
    }

    @Test
    @DisplayName("Ошибка валидации -> 400 с текстом первой ошибки поля")
    void validation_returns400WithFieldMessage() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult binding = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(binding);
        when(binding.getFieldErrors())
                .thenReturn(List.of(new FieldError("req", "hostname", "не должно быть пустым")));

        ResponseEntity<ErrorResponse> resp = handler.handleValidation(ex, request);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().message()).isEqualTo("не должно быть пустым");
    }

    @Test
    @DisplayName("Некорректный тип параметра -> 400 с именем параметра")
    void typeMismatch_returns400() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "id", null, new NumberFormatException());

        ResponseEntity<ErrorResponse> resp = handler.handleTypeMismatch(ex, request);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().message()).contains("id");
    }

    @Test
    @DisplayName("Нечитаемое тело -> 400 с общим сообщением")
    void notReadable_returns400() {
        ResponseEntity<ErrorResponse> resp = handler.handleNotReadable(
                new HttpMessageNotReadableException("parse error", (org.springframework.http.HttpInputMessage) null),
                request);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().message()).isEqualTo("Некорректный формат запроса");
    }

    @Test
    @DisplayName("IllegalArgumentException -> 400 с текстом исключения")
    void illegalArgument_returns400() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleIllegalArgument(new IllegalArgumentException("ConfigVersion not found: 42"), request);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().message()).isEqualTo("ConfigVersion not found: 42");
    }

    @Test
    @DisplayName("NoSuchElementException -> 404")
    void noSuchElement_returns404() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleNotFound(new NoSuchElementException("устройство не найдено"), request);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().message()).isEqualTo("устройство не найдено");
    }

    @Test
    @DisplayName("ResponseStatusException -> исходный статус и причина")
    void responseStatus_preservesStatusAndReason() {
        ResponseEntity<ErrorResponse> resp = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.CONFLICT, "конфликт версий"), request);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().message()).isEqualTo("конфликт версий");
    }

    @Test
    @DisplayName("Прочая ошибка -> 500 с обобщённым сообщением (детали уходят в лог)")
    void unexpected_returns500() {
        ResponseEntity<ErrorResponse> resp =
                handler.handleUnexpected(new RuntimeException("NPE внутри сервиса"), request);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().message()).isEqualTo("Внутренняя ошибка сервера");
        // Технические детали не утекают наружу
        assertThat(resp.getBody().message()).doesNotContain("NPE");
    }
}
