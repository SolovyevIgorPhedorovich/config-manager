package com.uniikm.configmanager.device.facade;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;

import com.uniikm.configmanager.common.dto.ConnectionProtocol;
import com.uniikm.configmanager.common.dto.DeviceCommandTarget;
import com.uniikm.configmanager.device.dto.terminal.TerminalSessionRequest;
import com.uniikm.configmanager.device.dto.terminal.TerminalSessionResponse;
import com.uniikm.configmanager.device.dto.terminal.TerminalWebSocketMessage;
import com.uniikm.configmanager.device.dto.terminal.TerminalWebSocketResponse;
import com.uniikm.configmanager.device.dto.mapper.TerminalMapper;
import com.uniikm.configmanager.device.model.DeviceInfo;
import com.uniikm.configmanager.device.service.DeviceService;
import com.uniikm.configmanager.device.service.DeviceTerminalService;
import com.uniikm.configmanager.device.service.DeviceTerminalService.TerminalSession;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TerminalFacadeImpl implements TerminalFacade {
    
    private final DeviceTerminalService terminalService;
    private final DeviceService deviceService;
    private final TerminalMapper terminalMapper;
    
    @Override
    public TerminalSessionResponse createSession(Long deviceId, TerminalSessionRequest request) {
        log.info("Creating terminal session for device: {}", deviceId);
        
        // 1. Получаем устройство
        DeviceInfo device = deviceService.getById(deviceId);
        
        // 2. Определяем протокол
        ConnectionProtocol protocol = determineProtocol(device, request);
        
        // 3. Создаём цель подключения
        DeviceCommandTarget target = createCommandTarget(request, protocol);
        
        // 4. Создаём сессию (sessionId это String)
        TerminalSession session = terminalService.createSession(deviceId, target);
        
        // 5. Маппим в ответ
        return terminalMapper.toResponse(session, device, protocol);
    }
    
    @Override
    public TerminalSessionResponse getSession(String sessionId) {
        log.debug("Getting terminal session: {}", sessionId);
        
        TerminalSession session = terminalService.getSession(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        
        // Получаем информацию об устройстве для полного ответа
        DeviceInfo device = deviceService.getById(session.getDeviceId());
        ConnectionProtocol protocol = determineProtocolFromDevice(device);
        
        return terminalMapper.toResponse(session, device, protocol);
    }
    
    @Override
    public void closeSession(String sessionId) {
        log.info("Closing terminal session: {}", sessionId);
        terminalService.closeSession(sessionId);
    }
    
    @Override
    public CompletableFuture<TerminalWebSocketResponse> processTerminalInput(
            TerminalWebSocketMessage message) {
        
        log.debug("Processing terminal input for session: {}", message.sessionId());
        
        // message.sessionId() возвращает String, передаём String в sendInput
        return terminalService.sendInput(message.sessionId(), message.input())
            .thenApply(this::mapToWebSocketResponse)
            .exceptionally(throwable -> {
                log.error("Error processing terminal input for session {}", 
                    message.sessionId(), throwable);
                return TerminalWebSocketResponse.error(
                    throwable.getMessage() != null ? 
                        throwable.getMessage() : "Internal error"
                );
            });
    }
    
    // Приватные методы
    
    private DeviceCommandTarget createCommandTarget(
            TerminalSessionRequest request, 
            ConnectionProtocol protocol) {
        
        return new DeviceCommandTarget(
            request.host(),
            request.port(),
            request.user(),
            request.password(),
            protocol,
            request.community(),
            request.useSsl() != null ? request.useSsl() : false,
            request.skipCertificateCheck() != null ? request.skipCertificateCheck() : false
        );
    }
    
    private ConnectionProtocol determineProtocol(
            DeviceInfo device, 
            TerminalSessionRequest request) {
        
        // Если протокол явно указан в запросе - используем его
        if (request.protocol() != null) {
            return request.protocol();
        }
        
        // Иначе определяем по устройству
        return determineProtocolFromDevice(device);
    }
    
    private ConnectionProtocol determineProtocolFromDevice(DeviceInfo device) {
        if (device.getOsVersion() != null) {
            return switch (device.getOsVersion().getName().toUpperCase()) {
                case "WINDOWS", "WINDOWS_SERVER" -> ConnectionProtocol.WINRM;
                case "LINUX", "UNIX", "UBUNTU", "DEBIAN", "CENTOS", 
                     "REDHAT", "MACOS" -> ConnectionProtocol.SSH;
                case "CISCO", "CISCO_IOS", "CISCO_NXOS" -> ConnectionProtocol.SSH;
                default -> {
                    log.warn("Unknown OS type: {}, defaulting to SSH", device.getOsVersion().getName());
                    yield ConnectionProtocol.SSH;
                }
            };
        }
        
        log.warn("No OS type for device: {}, defaulting to SSH", device.getId());
        return ConnectionProtocol.SSH;
    }
    
    private TerminalWebSocketResponse mapToWebSocketResponse(Map<String, Object> result) {
        boolean success = Boolean.TRUE.equals(result.get("success"));
        String output = (String) result.getOrDefault(
            "output", 
            result.getOrDefault("stdout", "")
        );
        String error = (String) result.getOrDefault(
            "error",
            result.getOrDefault("stderr", null)
        );
        Integer exitCode = result.get("exitCode") != null ? 
            ((Number) result.get("exitCode")).intValue() : 
            (success ? 0 : -1);
        
        return new TerminalWebSocketResponse(success, output, error, exitCode);
    }
}