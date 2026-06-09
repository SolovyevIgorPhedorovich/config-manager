package com.uniikm.configmanager.device.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniikm.configmanager.auth.utils.CustomUserDetails;
import com.uniikm.configmanager.device.dto.terminal.TerminalWebSocketMessage;
import com.uniikm.configmanager.device.facade.TerminalFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private final TerminalFacade terminalFacade;
    private final ObjectMapper objectMapper;

    // WebSocket-сессия → терминальная сессия
    private final Map<String, String> wsToTerminalSessions = new ConcurrentHashMap<>();
    // WebSocket-сессии, для которых уже подключён стриминг вывода (чтобы подписаться один раз)
    private final Set<String> boundSessions = ConcurrentHashMap.newKeySet();

    public TerminalWebSocketHandler(TerminalFacade terminalFacade, ObjectMapper objectMapper) {
        this.terminalFacade = terminalFacade;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connection established: {}", session.getId());
        sendJson(session, Map.of("type", "connection", "status", "established"));
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Путь /ws/** в http-цепочке permitAll, поэтому SecurityContext в потоке
        // обработки сообщений пуст. Восстанавливаем пользователя из атрибутов
        // рукопожатия, чтобы аудит действий терминала знал, кто их выполнил.
        applySecurityContext(session);
        try {
            doHandleTextMessage(session, message);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void doHandleTextMessage(WebSocketSession session, TextMessage message) {
        TerminalWebSocketMessage msg;
        try {
            msg = objectMapper.readValue(message.getPayload(), TerminalWebSocketMessage.class);
        } catch (Exception e) {
            log.error("Error parsing WebSocket message", e);
            sendJson(session, Map.of("success", false, "error", "Invalid message format: " + e.getMessage()));
            return;
        }

        String sessionId = msg.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sendJson(session, Map.of("success", false, "error", "sessionId is required"));
            return;
        }
        wsToTerminalSessions.put(session.getId(), sessionId);

        // Подписываем WebSocket на потоковый вывод терминала один раз.
        bindOutput(session, sessionId);

        String type = msg.type() == null ? "input" : msg.type();

        // Логируем приём данных. DEBUG — метаданные, TRACE — содержимое.
        if (log.isDebugEnabled()) {
            int inLen = msg.input() != null ? msg.input().length() : 0;
            log.debug("WS<- ws={} term={} type={} inLen={} cols={} rows={}",
                    session.getId(), sessionId, type, inLen, msg.cols(), msg.rows());
            if (log.isTraceEnabled() && msg.input() != null && !msg.input().isEmpty()) {
                log.trace("WS<- ws={} input='{}'", session.getId(), preview(msg.input(), 200));
            }
        }

        try {
            switch (type) {
                case "init" -> {
                    if (msg.cols() != null && msg.rows() != null) {
                        terminalFacade.resize(sessionId, msg.cols(), msg.rows());
                    }
                }
                case "resize" -> {
                    if (msg.cols() != null && msg.rows() != null) {
                        terminalFacade.resize(sessionId, msg.cols(), msg.rows());
                    }
                }
                default -> handleInput(session, sessionId, msg.input());
            }
        } catch (IllegalArgumentException e) {
            // сессия не найдена / закрыта
            sendJson(session, Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error handling terminal message for session {}", sessionId, e);
            sendJson(session, Map.of("success", false, "error", "Internal error: " + e.getMessage()));
        }
    }

    private void handleInput(WebSocketSession session, String sessionId, String input) {
        if (terminalFacade.isInteractive(sessionId)) {
            // Интерактивно: пишем нажатия в PTY, вывод придёт асинхронно через стрим.
            terminalFacade.writeInput(sessionId, input);
            return;
        }
        // Неинтерактивно (WinRM/SNMP): команда → ответ.
        terminalFacade.processTerminalInput(new TerminalWebSocketMessage(sessionId, "input", input, null, null))
            .thenAccept(response -> sendJson(session, response))
            .exceptionally(throwable -> {
                log.error("Error processing terminal input for session {}", sessionId, throwable);
                sendJson(session, Map.of("success", false,
                        "error", "Processing error: " + throwable.getMessage()));
                return null;
            });
    }

    private void bindOutput(WebSocketSession session, String sessionId) {
        if (!boundSessions.add(session.getId())) {
            return; // уже подписан
        }
        try {
            terminalFacade.attachOutput(sessionId, text -> sendJson(session, Map.of("output", text)));
        } catch (Exception e) {
            boundSessions.remove(session.getId());
            log.debug("Не удалось подписать вывод для сессии {}: {}", sessionId, e.getMessage());
            sendJson(session, Map.of("success", false, "error", e.getMessage()));
        }
    }

    /** Потокобезопасная отправка JSON в WebSocket (sendMessage не потокобезопасен). */
    private void sendJson(WebSocketSession session, Object payload) {
        try {
            if (!session.isOpen()) return;
            String json = objectMapper.writeValueAsString(payload);
            // Логируем отправку данных. DEBUG — размер, TRACE — содержимое.
            if (log.isDebugEnabled()) {
                log.debug("WS-> ws={} bytes={}", session.getId(), json.length());
                if (log.isTraceEnabled()) {
                    log.trace("WS-> ws={} data='{}'", session.getId(), preview(json, 200));
                }
            }
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.debug("Error sending WebSocket message for session {}: {}", session.getId(), e.getMessage());
        }
    }

    /**
     * Восстанавливает SecurityContext текущего потока из атрибутов рукопожатия
     * (username/userId/roles положил JwtHandshakeInterceptor). Principal —
     * CustomUserDetails, чтобы SecurityFacade.currentUserId() в аудите вернул id.
     */
    @SuppressWarnings("unchecked")
    private void applySecurityContext(WebSocketSession session) {
        Map<String, Object> attrs = session.getAttributes();
        Object username = attrs.get("username");
        if (username == null) {
            return;
        }
        Object userIdObj = attrs.get("userId");
        Long userId = (userIdObj instanceof Number n) ? n.longValue() : null;
        List<String> roles = (List<String>) attrs.getOrDefault("roles", List.of());
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        CustomUserDetails principal = new CustomUserDetails(userId, username.toString(), authorities);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    /** Усечение + экранирование управляющих символов для читаемых логов. */
    private static String preview(String s, int max) {
        if (s == null) return "";
        String esc = s.replace("", "\\e")
                      .replace("\r", "\\r")
                      .replace("\n", "\\n")
                      .replace("\t", "\\t");
        return esc.length() <= max ? esc : esc.substring(0, max) + "…(len=" + s.length() + ")";
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
        cleanup(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket connection closed: {} status: {}", session.getId(), status);
        cleanup(session);
    }

    private void cleanup(WebSocketSession session) {
        boundSessions.remove(session.getId());
        String terminalSessionId = wsToTerminalSessions.remove(session.getId());
        if (terminalSessionId != null) {
            try {
                terminalFacade.closeSession(terminalSessionId);
                log.info("Terminal session {} closed due to WebSocket closure", terminalSessionId);
            } catch (Exception e) {
                log.error("Error closing terminal session {} on WebSocket close", terminalSessionId, e);
            }
        }
    }
}
