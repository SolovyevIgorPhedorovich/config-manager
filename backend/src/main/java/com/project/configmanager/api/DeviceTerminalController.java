package com.project.configmanager.api;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.configmanager.device.service.DeviceTerminalService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/devices/{id}/terminal")
@RequiredArgsConstructor
public class DeviceTerminalController {

    private final DeviceTerminalService terminalService;

    @PostMapping("/session")
    public ResponseEntity<Map<String, String>> createSession(
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> credentials
    ) {
        var session = terminalService.createSession(id, credentials);

        return ResponseEntity.ok(Map.of(
                "sessionId", session.getId(),
                "state", session.getState().name()
        ));
    }

    @DeleteMapping("/{sessionId}")
    public void close(@PathVariable String sessionId) {
        terminalService.closeSession(sessionId);
    }
}
