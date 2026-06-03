package com.uniikm.configmanager.device.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniikm.configmanager.device.dto.terminal.TerminalWebSocketMessage;
import com.uniikm.configmanager.device.facade.TerminalFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class TerminalWebSocketHandler extends TextWebSocketHandler {
    
    private final TerminalFacade terminalFacade;
    private final ObjectMapper objectMapper;
    
    // Храним маппинг WebSocket сессий к терминальным сессиям
    private final Map<String, String> wsToTerminalSessions = new ConcurrentHashMap<>();
    
    public TerminalWebSocketHandler(TerminalFacade terminalFacade, ObjectMapper objectMapper) {
        this.terminalFacade = terminalFacade;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connection established: {}", session.getId());
        // Можно отправить приветственное сообщение
        try {
            session.sendMessage(new TextMessage(
                objectMapper.writeValueAsString(
                    Map.of("type", "connection", "status", "established")
                )
            ));
        } catch (Exception e) {
            log.error("Error sending welcome message", e);
        }
    }
    
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            // Парсим входящее сообщение
            TerminalWebSocketMessage terminalMessage = objectMapper.readValue(
                message.getPayload(), 
                TerminalWebSocketMessage.class
            );
            
            // Сохраняем связь WebSocket сессии и терминальной сессии
            wsToTerminalSessions.put(session.getId(), terminalMessage.sessionId());
            
            // Обрабатываем через фасад
            terminalFacade.processTerminalInput(terminalMessage)
                .thenAccept(response -> {
                    try {
                        if (session.isOpen()) {
                            session.sendMessage(new TextMessage(
                                objectMapper.writeValueAsString(response)
                            ));
                        }
                    } catch (Exception e) {
                        log.error("Error sending WebSocket response for session {}", 
                            session.getId(), e);
                    }
                })
                .exceptionally(throwable -> {
                    log.error("Error in terminal processing for session {}", 
                        session.getId(), throwable);
                    try {
                        if (session.isOpen()) {
                            session.sendMessage(new TextMessage(
                                objectMapper.writeValueAsString(
                                    Map.of(
                                        "success", false,
                                        "error", "Internal processing error: " + 
                                            throwable.getMessage()
                                    )
                                )
                            ));
                        }
                    } catch (Exception ex) {
                        log.error("Error sending error response", ex);
                    }
                    return null;
                });
                
        } catch (Exception e) {
            log.error("Error parsing WebSocket message", e);
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "success", false,
                                "error", "Invalid message format: " + e.getMessage()
                            )
                        )
                    ));
                }
            } catch (Exception ex) {
                log.error("Error sending parse error response", ex);
            }
        }
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error for session {}: {}", 
            session.getId(), exception.getMessage());
        
        // Закрываем связанную терминальную сессию
        String terminalSessionId = wsToTerminalSessions.remove(session.getId());
        if (terminalSessionId != null) {
            try {
                terminalFacade.closeSession(terminalSessionId);
            } catch (Exception e) {
                log.error("Error closing terminal session {} on transport error", 
                    terminalSessionId, e);
            }
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket connection closed: {} status: {}", 
            session.getId(), status);
        
        // Автоматически закрываем терминальную сессию при закрытии WebSocket
        String terminalSessionId = wsToTerminalSessions.remove(session.getId());
        if (terminalSessionId != null) {
            try {
                terminalFacade.closeSession(terminalSessionId);
                log.info("Terminal session {} closed due to WebSocket closure", 
                    terminalSessionId);
            } catch (Exception e) {
                log.error("Error closing terminal session {} on WebSocket close", 
                    terminalSessionId, e);
            }
        }
    }
}