package com.uniikm.configmanager.device.dto.terminal;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TerminalWebSocketResponse(
    boolean success,
    String output,
    String error,
    Integer exitCode
) {
    public static TerminalWebSocketResponse success(String output) {
        return new TerminalWebSocketResponse(true, output, null, 0);
    }
    
    public static TerminalWebSocketResponse error(String error) {
        return new TerminalWebSocketResponse(false, null, error, -1);
    }
    
    public static TerminalWebSocketResponse commandResult(
            boolean success, String output, String error, int exitCode) {
        return new TerminalWebSocketResponse(success, output, error, exitCode);
    }
}