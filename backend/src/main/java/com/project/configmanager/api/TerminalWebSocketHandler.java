package com.project.configmanager.api;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.configmanager.device.service.DeviceTerminalService;

@Component
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private final DeviceTerminalService terminalService;

    public TerminalWebSocketHandler(DeviceTerminalService terminalService) {
        this.terminalService = terminalService;
    }

    @Override
public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

    JsonNode json = new ObjectMapper().readTree(message.getPayload());

    String sessionId = json.get("sessionId").asText();
    String input = json.get("input").asText();

    var result = terminalService.sendInput(sessionId, input).get();

    session.sendMessage(new TextMessage(
        new ObjectMapper().writeValueAsString(result)
    ));
}
}