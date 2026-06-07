package com.uniikm.configmanager.audit.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.uniikm.configmanager.audit.dto.EventLogDto;
import com.uniikm.configmanager.audit.service.EventQueryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/event")
@RequiredArgsConstructor
public class EventController {

    private final EventQueryService eventQueryService;

    @GetMapping("/logs")
    public List<EventLogDto> getLogs(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) List<Long> deviceIds,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        if (deviceId != null) {
            return eventQueryService.getDeviceLogs(deviceId);
        }
        if (deviceIds != null && !deviceIds.isEmpty()) {
            return eventQueryService.getDeviceLogs(deviceIds);
        }
        return eventQueryService.getAllLogs(page, size);
    }
}
