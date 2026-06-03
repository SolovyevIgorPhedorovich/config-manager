package com.uniikm.configmanager.device.controller;


import com.uniikm.configmanager.device.dto.terminal.TerminalSessionRequest;
import com.uniikm.configmanager.device.dto.terminal.TerminalSessionResponse;
import com.uniikm.configmanager.device.facade.TerminalFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/devices/{deviceId}/terminal")
@RequiredArgsConstructor
public class DeviceTerminalController {
    
    private final TerminalFacade terminalFacade;
    
    @PostMapping("/session")
    public ResponseEntity<TerminalSessionResponse> createSession(
            @PathVariable Long deviceId,
            @Validated @RequestBody TerminalSessionRequest request
    ) {
        return ResponseEntity.ok(
            terminalFacade.createSession(deviceId, request)
        );
    }
    
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> closeSession(@PathVariable String sessionId) {
        terminalFacade.closeSession(sessionId);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/{sessionId}")
    public ResponseEntity<TerminalSessionResponse> getSession(@PathVariable String sessionId) {
        return ResponseEntity.ok(terminalFacade.getSession(sessionId));
    }
}