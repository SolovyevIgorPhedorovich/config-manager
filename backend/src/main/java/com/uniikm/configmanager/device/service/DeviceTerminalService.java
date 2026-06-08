package com.uniikm.configmanager.device.service;

import com.uniikm.configmanager.common.dto.DeviceCommandTarget;
import com.uniikm.configmanager.common.terminal.RemoteConnection;
import com.uniikm.configmanager.common.terminal.RemoteConnectionFactory;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTerminalService {
    
    private final RemoteConnectionFactory connectionFactory;
    private final ConcurrentHashMap<String, TerminalSession> sessions = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    
    // Периодическая очистка неактивных сессий
    {
        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
        cleaner.scheduleAtFixedRate(this::cleanupStaleSessions, 5, 5, TimeUnit.MINUTES);
    }
    
    /**
     * Создать новую терминальную сессию
     * @param deviceId ID устройства
     * @param target параметры подключения
     * @return созданная сессия с String ID
     */
    public TerminalSession createSession(Long deviceId, DeviceCommandTarget target) {
        // Генерируем UUID и преобразуем в строку
        String sessionId = UUID.randomUUID().toString();
        
        RemoteConnection connection = connectionFactory.create(target);
        
        TerminalSession session = new TerminalSession(sessionId, deviceId, connection);
        sessions.put(sessionId, session);
        
        // Асинхронное подключение
        executor.submit(() -> connectAsync(session, deviceId));
        
        log.info("Terminal session created: {} for device: {}", sessionId, deviceId);
        return session;
    }
    
    /**
     * Отправить команду в терминал
     * @param sessionId строковой ID сессии
     * @param input команда
     * @return результат выполнения
     */
    public CompletableFuture<Map<String, Object>> sendInput(String sessionId, String input) {
        TerminalSession session = getSessionOrThrow(sessionId);
        
        if (session.getState() != SessionState.CONNECTED) {
            return CompletableFuture.completedFuture(
                Map.of(
                    "success", false, 
                    "error", "Session not connected. Current state: " + session.getState()
                )
            );
        }
        
        log.debug("Sending input to session {}: {}", sessionId, input);
        
        return session.getConnection().executeCommand(input)
            .orTimeout(30, TimeUnit.SECONDS)
            .exceptionally(throwable -> {
                log.error("Command execution failed for session {}", sessionId, throwable);
                return Map.of(
                    "success", false,
                    "error", "Command execution failed: " + throwable.getMessage(),
                    "exitCode", -1
                );
            });
    }
    
    /** Является ли соединение сессии интерактивным (PTY-shell со стримингом). */
    public boolean isInteractive(String sessionId) {
        TerminalSession session = sessions.get(sessionId);
        return session != null && session.getConnection().isInteractive();
    }

    /**
     * Записать нажатия клавиш в интерактивную сессию. Вывод приходит асинхронно
     * через слушателя, заданного в {@link #attachOutput}.
     */
    public void writeInput(String sessionId, String data) {
        TerminalSession session = getSessionOrThrow(sessionId);
        if (session.getState() != SessionState.CONNECTED) {
            log.debug("Ввод в неподключённую сессию {} (состояние {}) — отброшен",
                sessionId, session.getState());
            return;
        }
        session.getConnection().write(data);
    }

    /** Подписать потребителя на потоковый вывод интерактивной сессии. */
    public void attachOutput(String sessionId, java.util.function.Consumer<String> consumer) {
        TerminalSession session = getSessionOrThrow(sessionId);
        session.getConnection().onOutput(consumer);
    }

    /** Изменить размер PTY. */
    public void resize(String sessionId, int cols, int rows) {
        TerminalSession session = sessions.get(sessionId);
        if (session != null) {
            session.getConnection().resize(cols, rows);
        }
    }

    /**
     * Закрыть сессию
     * @param sessionId строковой ID сессии
     */
    public void closeSession(String sessionId) {
        TerminalSession session = sessions.remove(sessionId);
        if (session != null) {
            disconnectSession(session);
            log.info("Terminal session {} closed", sessionId);
        } else {
            log.warn("Attempted to close non-existent session: {}", sessionId);
        }
    }
    
    /**
     * Получить сессию по ID
     * @param id строковой ID сессии
     * @return сессия или null
     */
    public TerminalSession getSession(String id) {
        return sessions.get(id);
    }
    
    /**
     * Получить все активные сессии для мониторинга
     */
    public Map<String, SessionState> getActiveSessions() {
        return sessions.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().getState()
            ));
    }
    
    // Приватные методы
    
    private void connectAsync(TerminalSession session, Long deviceId) {
        try {
            session.setState(SessionState.CONNECTING);
            
            var result = session.getConnection()
                .connect()
                .get(30, TimeUnit.SECONDS);
            
            if (Boolean.TRUE.equals(result.get("success"))) {
                session.setState(SessionState.CONNECTED);
                log.info("Terminal session {} connected to device {}", 
                    session.getId(), deviceId);
            } else {
                session.setState(SessionState.FAILED);
                session.setError(String.valueOf(result.getOrDefault("error", "Unknown error")));
                log.warn("Terminal session {} connection failed: {}", 
                    session.getId(), session.getError());
            }
        } catch (TimeoutException e) {
            session.setState(SessionState.FAILED);
            session.setError("Connection timeout");
            log.error("Terminal session {} connection timeout", session.getId());
        } catch (Exception e) {
            session.setState(SessionState.FAILED);
            session.setError(e.getMessage());
            log.error("Terminal session {} connection error", session.getId(), e);
        }
    }
    
    private void disconnectSession(TerminalSession session) {
        try {
            session.getConnection().disconnect();
            session.setState(SessionState.CLOSED);
        } catch (Exception e) {
            log.error("Error disconnecting session {}", session.getId(), e);
        }
    }
    
    private TerminalSession getSessionOrThrow(String id) {
        TerminalSession session = sessions.get(id);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + id);
        }
        return session;
    }
    
    private void cleanupStaleSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> {
            TerminalSession session = entry.getValue();
            boolean isStale = session.getState() == SessionState.FAILED ||
                             session.getState() == SessionState.CLOSED ||
                             (session.getCreatedAt() + TimeUnit.HOURS.toMillis(1) < now);
            
            if (isStale) {
                log.info("Cleaning up stale session: {}", session.getId());
                disconnectSession(session);
            }
            return isStale;
        });
    }
    
    @Data
    public static class TerminalSession {
        private final String id;  // Всегда String
        private final Long deviceId;
        private final RemoteConnection connection;
        private final long createdAt = System.currentTimeMillis();
        private volatile SessionState state = SessionState.CREATED;
        private volatile String error;
        
        public TerminalSession(String id, Long deviceId, RemoteConnection connection) {
            this.id = id;
            this.deviceId = deviceId;
            this.connection = connection;
        }
    }
    
    public enum SessionState {
        CREATED, CONNECTING, CONNECTED, FAILED, CLOSED
    }
}