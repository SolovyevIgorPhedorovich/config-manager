package com.uniikm.configmanager.device.dto.terminal;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TerminalSessionResponse(
    String sessionId,      // String, не UUID
    String state,
    String deviceHostname,
    String protocol,
    Long createdAt,
    String error
) {
    // Конструктор без ошибки
    public TerminalSessionResponse(
            String sessionId, 
            String state, 
            String deviceHostname, 
            String protocol, 
            Long createdAt) {
        this(sessionId, state, deviceHostname, protocol, createdAt, null);
    }
}