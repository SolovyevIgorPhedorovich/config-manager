package com.project.configmanager.device.service;

import com.project.configmanager.device.model.DeviceInfo;
import com.project.configmanager.device.repository.DeviceRepository;
import com.project.configmanager.integration.ProtocolAdapter;
import com.project.configmanager.integration.ProtocolAdapterFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class DeviceTerminalService {

    private final ProtocolAdapterFactory adapterFactory;
    private final DeviceRepository deviceRepository;

    private final ConcurrentHashMap<String, TerminalSession> sessions = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    // CREATE SESSION
    public TerminalSession createSession(Long deviceId, Map<String, String> credentials) {

        String sessionId = UUID.randomUUID().toString();

        DeviceInfo device = deviceRepository.findById(deviceId).orElseThrow(() -> new IllegalArgumentException("Устрой не найдено в базе данных: " + deviceId));

        ProtocolAdapter adapter = adapterFactory.create(device, credentials);

        TerminalSession session = new TerminalSession(
                sessionId,
                deviceId,
                adapter
        );

        sessions.put(sessionId, session);

        executor.submit(() -> {
            try {
                session.setState(SessionState.CONNECTING);

                var result = adapter.connect(credentials).get();

                if (Boolean.TRUE.equals(result.get("success"))) {
                    session.setState(SessionState.CONNECTED);
                } else {
                    session.setState(SessionState.FAILED);
                    session.setError(String.valueOf(result.get("error")));
                }

            } catch (Exception e) {
                session.setState(SessionState.FAILED);
                session.setError(e.getMessage());
            }
        });

        return session;
    }

    // EXEC INPUT (REAL-TIME)
    public CompletableFuture<Map<String, Object>> sendInput(String sessionId, String input) {

        TerminalSession session = get(sessionId);

        if (session.getState() != SessionState.CONNECTED) {
            return CompletableFuture.completedFuture(
                    Map.of("success", false, "error", "Session not connected")
            );
        }

        return session.getAdapter().executeCommand(input);
    }

    // CLOSE SESSION
    public void closeSession(String sessionId) {

        TerminalSession session = sessions.remove(sessionId);

        if (session != null) {
            try {
                session.getAdapter().disconnect();
                session.setState(SessionState.CLOSED);
            } catch (Exception ignored) {}
        }
    }

    public TerminalSession getSession(String id) {
        return sessions.get(id);
    }

    private TerminalSession get(String id) {
        TerminalSession session = sessions.get(id);

        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + id);
        }

        return session;
    }

    public static class TerminalSession {

        private final String id;
        private final Long deviceId;
        private final ProtocolAdapter adapter;

        private volatile SessionState state = SessionState.CREATED;
        private volatile String error;

        public TerminalSession(String id, Long deviceId, ProtocolAdapter adapter) {
            this.id = id;
            this.deviceId = deviceId;
            this.adapter = adapter;
        }

        public String getId() { return id; }
        public Long getDeviceId() { return deviceId; }
        public ProtocolAdapter getAdapter() { return adapter; }

        public SessionState getState() { return state; }
        public void setState(SessionState state) { this.state = state; }

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }

    public enum SessionState {
        CREATED,
        CONNECTING,
        CONNECTED,
        FAILED,
        CLOSED
    }
}