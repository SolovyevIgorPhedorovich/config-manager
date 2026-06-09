package com.uniikm.configmanager.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Глобальный обработчик ошибок REST-слоя.
 *
 * На интерфейс отдаётся краткое сообщение ({@link ErrorResponse#message()}),
 * а полная диагностика (тип исключения, стектрейс, путь, пользователь) пишется
 * в журнал — для 5xx с полным стектрейсом, для ожидаемых 4xx кратко (WARN).
 * Запись в файл /var/log/configmanager настраивается в logback-spring.xml.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Неверный логин/пароль. */
    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<ErrorResponse> handleAuth(AuthenticationException ex, HttpServletRequest req) {
        log.warn("Аутентификация отклонена [{}]: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль", req);
    }

    /** Недостаточно прав. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        log.warn("Доступ запрещён [{}]: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "Недостаточно прав для выполнения операции", req);
    }

    /** Ошибки валидации тела запроса (@Valid). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Ошибка валидации [{}]: {}", req.getRequestURI(), details);
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst().map(FieldError::getDefaultMessage)
                .orElse("Некорректные данные запроса");
        return build(HttpStatus.BAD_REQUEST, msg, req);
    }

    /** Некорректный тип параметра запроса (например, нечисловой id в пути). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        log.warn("Некорректный параметр [{}]: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Некорректное значение параметра: " + ex.getName(), req);
    }

    /** Нечитаемое/некорректное тело запроса. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        log.warn("Некорректное тело запроса [{}]: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Некорректный формат запроса", req);
    }

    /** Некорректные аргументы бизнес-логики. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        log.warn("Некорректный запрос [{}]: {}", req.getRequestURI(), ex.getMessage());
        String msg = ex.getMessage() != null ? ex.getMessage() : "Некорректный запрос";
        return build(HttpStatus.BAD_REQUEST, msg, req);
    }

    /** Запрашиваемый объект не найден. */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException ex, HttpServletRequest req) {
        log.warn("Объект не найден [{}]: {}", req.getRequestURI(), ex.getMessage());
        String msg = ex.getMessage() != null ? ex.getMessage() : "Запрашиваемый объект не найден";
        return build(HttpStatus.NOT_FOUND, msg, req);
    }

    /** Явно проброшенный ResponseStatusException — сохраняем его статус и причину. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String msg = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        if (status.is5xxServerError()) {
            log.error("Ошибка [{}] {}: {}", status.value(), req.getRequestURI(), msg, ex);
        } else {
            log.warn("[{}] {}: {}", status.value(), req.getRequestURI(), msg);
        }
        return build(status, msg, req);
    }

    /** Любая прочая ошибка — внутренняя ошибка сервера (полный стектрейс в лог). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Внутренняя ошибка сервера [{} {}]: {}",
                req.getMethod(), req.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера", req);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest req) {
        ErrorResponse body = ErrorResponse.of(
                status.value(), status.getReasonPhrase(), message, req.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
