package com.uniikm.configmanager.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.uniikm.configmanager.device.websocket.TerminalWebSocketHandler;

import lombok.RequiredArgsConstructor;


@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final TerminalWebSocketHandler handler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/terminal")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins(
                    "http://localhost:5174",
                        "http://localhost:5173",
                        "http://localhost:4173",
                        "http://localhost:4174"
                );
    }
}