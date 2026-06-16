package com.uniikm.configmanager.audit.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.uniikm.configmanager.audit.dto.EventLogDto;
import com.uniikm.configmanager.audit.facade.AuditFacade;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/event")
@RequiredArgsConstructor
public class EventController {

    private final AuditFacade auditFacade;

    @GetMapping("/logs")
    public List<EventLogDto> getLogs(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) List<Long> deviceIds,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        if (deviceId != null) {
            return auditFacade.getDeviceLogs(deviceId);
        }
        if (deviceIds != null && !deviceIds.isEmpty()) {
            return auditFacade.getDeviceLogs(deviceIds);
        }
        return auditFacade.getAllLogs(page, size);
    }
}
