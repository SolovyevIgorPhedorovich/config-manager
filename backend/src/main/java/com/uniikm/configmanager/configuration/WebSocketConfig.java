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
                // Разрешаем любой origin: WS идёт напрямую (не через vite-прокси),
                // и фикс-список портов ломал рукопожатие при отдаче сборки с другого
                // порта. Доступ всё равно защищён JWT в JwtHandshakeInterceptor.
                .setAllowedOriginPatterns("*");
    }
}