package com.uniikm.configmanager.device.dto.terminal;


public record TerminalWebSocketMessage(
    String sessionId,
    String input
){}
