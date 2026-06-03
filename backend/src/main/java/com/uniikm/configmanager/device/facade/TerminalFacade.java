package com.uniikm.configmanager.device.facade;

import java.util.concurrent.CompletableFuture;

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
}
