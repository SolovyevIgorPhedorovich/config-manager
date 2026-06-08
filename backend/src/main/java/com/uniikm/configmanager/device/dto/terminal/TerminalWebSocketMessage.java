package com.uniikm.configmanager.device.dto.terminal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Сообщение от клиента веб-терминала.
 *
 * type:
 *   "init"   — привязать WebSocket к сессии и начать стриминг вывода (cols/rows — размер PTY)
 *   "input"  — нажатия клавиш (поле input)
 *   "resize" — изменение размера терминала (cols/rows)
 *   null     — трактуется как "input" (обратная совместимость)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TerminalWebSocketMessage(
    String sessionId,
    String type,
    String input,
    Integer cols,
    Integer rows
){}
