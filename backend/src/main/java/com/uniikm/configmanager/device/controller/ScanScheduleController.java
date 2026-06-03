package com.uniikm.configmanager.device.controller;

import com.uniikm.configmanager.device.dto.ScanScheduleDto;
import com.uniikm.configmanager.device.model.ScanScheduleConfig;
import com.uniikm.configmanager.device.service.ScanScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/devices/scan-schedules")
@RequiredArgsConstructor
public class ScanScheduleController {

    private final ScanScheduleService service;

    @GetMapping
    public List<ScanScheduleConfig> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public ScanScheduleConfig getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    public ScanScheduleConfig create(@RequestBody ScanScheduleDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public ScanScheduleConfig update(@PathVariable Long id, @RequestBody ScanScheduleDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/run")
    public ResponseEntity<Map<String, Object>> runNow(@PathVariable Long id) {
        return service.runNow(id);
    }
}
