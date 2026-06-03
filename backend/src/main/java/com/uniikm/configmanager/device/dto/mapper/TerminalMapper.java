package com.uniikm.configmanager.device.dto.mapper;

import com.uniikm.configmanager.common.dto.ConnectionProtocol;
import com.uniikm.configmanager.device.dto.terminal.TerminalSessionResponse;
import com.uniikm.configmanager.device.model.DeviceInfo;
import com.uniikm.configmanager.device.service.DeviceTerminalService.TerminalSession;
import org.springframework.stereotype.Component;

@Component
public class TerminalMapper {
    
    public TerminalSessionResponse toResponse(
            TerminalSession session, 
            DeviceInfo device, 
            ConnectionProtocol protocol) {
        
        return new TerminalSessionResponse(
            session.getId(),                                    // String sessionId
            session.getState() != null ? session.getState().name() : "UNKNOWN",
            device != null ? device.getHostname() : "unknown",
            protocol != null ? protocol.name() : "unknown",
            session.getCreatedAt(),
            session.getError()
        );
    }
}