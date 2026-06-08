package com.uniikm.configmanager.device.facade;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.uniikm.configmanager.device.dto.terminal.TerminalSessionRequest;
import com.uniikm.configmanager.device.dto.terminal.TerminalSessionResponse;
import com.uniikm.configmanager.device.dto.terminal.TerminalWebSocketMessage;
import com.uniikm.configmanager.device.dto.terminal.TerminalWebSocketResponse;

public interface TerminalFacade {


    public TerminalSessionResponse createSession(Long deviceId, TerminalSessionRequest request);

    public void closeSession(String sessionId);

    public TerminalSessionResponse getSession(String sessionId);

    public CompletableFuture<TerminalWebSocketResponse> processTerminalInput(
            TerminalWebSocketMessage message);

    // ── Интерактивный (PTY) режим ──────────────────────────────────────────
    boolean isInteractive(String sessionId);

    void writeInput(String sessionId, String data);

    void attachOutput(String sessionId, Consumer<String> consumer);

    void resize(String sessionId, int cols, int rows);
}
