package com.uniikm.configmanager.config.controller;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.uniikm.configmanager.config.dto.ApplyConfigRequest;
import com.uniikm.configmanager.config.dto.ApplyConfigResponse;
import com.uniikm.configmanager.config.dto.ConfigCompareRequest;
import com.uniikm.configmanager.config.dto.ConfigCompareResponse;
import com.uniikm.configmanager.config.dto.ConfigHistoryResponse;
import com.uniikm.configmanager.config.dto.ConfigStatusResponse;
import com.uniikm.configmanager.config.dto.TemplateAssignmentResponse;
import com.uniikm.configmanager.config.facade.ConfigFacade;
import com.uniikm.configmanager.config.service.TemplateService;


@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigFacade configFacade;
    private final TemplateService templateService;

    @PostMapping("/devices/configure")
    public ResponseEntity<ApplyConfigResponse> configureDevices(@Validated @RequestBody ApplyConfigRequest request) {
        return ResponseEntity.ok(configFacade.apply(request));
    }
    
    @GetMapping("/devices/{deviceId}/config/history")
    public ResponseEntity<ConfigHistoryResponse> getConfigHistory(@PathVariable Long deviceId) {
        ConfigHistoryResponse history = configFacade.getHistory(deviceId);
        return ResponseEntity.ok(history);
    }

    @PostMapping("/config/compare")
    public ResponseEntity<ConfigCompareResponse> compareConfigs(@Validated @RequestBody ConfigCompareRequest request) {
        ConfigCompareResponse response = configFacade.compare(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/config/tasks/{taskGroupId}/status")
    public ResponseEntity<List<ConfigStatusResponse>> getTaskStatus(@PathVariable String taskGroupId) {
         return ResponseEntity.ok(configFacade.getStatus(taskGroupId));
    }

    /** Шаблоны, привязанные к конкретному устройству. */
    @GetMapping("/devices/{deviceId}/templates")
    public ResponseEntity<List<TemplateAssignmentResponse>> getDeviceTemplates(@PathVariable Long deviceId) {
        return ResponseEntity.ok(templateService.getDeviceAssignments(deviceId));
    }
}