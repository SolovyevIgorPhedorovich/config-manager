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
import com.fasterxml.jackson.databind.JsonNode;
import com.uniikm.configmanager.config.dto.ApplyConfigResponse;
import com.uniikm.configmanager.config.dto.ConfigStatusResponse;
import com.uniikm.configmanager.config.dto.DriftComparisonResponse;
import com.uniikm.configmanager.config.dto.ResolveDriftRequest;
import com.uniikm.configmanager.config.dto.ScheduledApplyResponse;
import com.uniikm.configmanager.config.dto.TemplateAssignmentResponse;
import com.uniikm.configmanager.config.facade.ConfigFacade;
import com.uniikm.configmanager.config.service.DeviceConfigQueryService;
import com.uniikm.configmanager.config.service.ScheduledConfigApplyService;
import com.uniikm.configmanager.config.service.TemplateService;


@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigFacade configFacade;
    private final TemplateService templateService;
    private final ScheduledConfigApplyService scheduledConfigApplyService;
    private final DeviceConfigQueryService deviceConfigQueryService;

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

    /** Фактический статус применения группы задач (для опроса результата из интерфейса). */
    @GetMapping("/config/apply/{groupTaskId}/status")
    public ResponseEntity<ConfigStatusResponse> getApplyGroupStatus(@PathVariable String groupTaskId) {
        return ResponseEntity.ok(configFacade.getApplyGroupStatus(groupTaskId));
    }

    /** Шаблоны, привязанные к конкретному устройству. */
    @GetMapping("/devices/{deviceId}/templates")
    public ResponseEntity<List<TemplateAssignmentResponse>> getDeviceTemplates(@PathVariable Long deviceId) {
        return ResponseEntity.ok(templateService.getDeviceAssignments(deviceId));
    }

    // ── Очередь отложенного применения (устройство было офлайн) ────────────────

    /** Все ожидающие применения (устройства офлайн). */
    @GetMapping("/config/scheduled")
    public ResponseEntity<List<ScheduledApplyResponse>> getScheduled() {
        return ResponseEntity.ok(scheduledConfigApplyService.listPending());
    }

    /** Ожидающие применения для конкретного устройства. */
    @GetMapping("/devices/{deviceId}/config/scheduled")
    public ResponseEntity<List<ScheduledApplyResponse>> getDeviceScheduled(@PathVariable Long deviceId) {
        return ResponseEntity.ok(scheduledConfigApplyService.listByDevice(deviceId));
    }

    /** Отменить отложенное применение. */
    @DeleteMapping("/config/scheduled/{id}")
    public ResponseEntity<Void> cancelScheduled(@PathVariable Long id) {
        scheduledConfigApplyService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    // ── Конфигурация устройства: активная версия, drift, шаблон ────────────────

    /** Активная (текущая) конфигурация устройства — для предзаполнения редактора. */
    @GetMapping("/devices/{deviceId}/config/active")
    public ResponseEntity<JsonNode> getActiveConfig(@PathVariable Long deviceId) {
        JsonNode config = deviceConfigQueryService.getActiveConfig(deviceId);
        return config != null ? ResponseEntity.ok(config) : ResponseEntity.noContent().build();
    }

    /** Активная конфигурация в виде параметризованного шаблона (уникальные поля → переменные). */
    @GetMapping("/devices/{deviceId}/config/as-template")
    public ResponseEntity<JsonNode> getConfigAsTemplate(@PathVariable Long deviceId) {
        return ResponseEntity.ok(deviceConfigQueryService.getConfigAsTemplate(deviceId));
    }

    /** Сравнение сохранённой и фактической конфигураций (для разрешения расхождения). */
    @GetMapping("/devices/{deviceId}/config/drift")
    public ResponseEntity<DriftComparisonResponse> getDriftComparison(@PathVariable Long deviceId) {
        return ResponseEntity.ok(deviceConfigQueryService.getDriftComparison(deviceId));
    }

    /** Разрешить расхождение конфигурации (drift). */
    @PostMapping("/devices/{deviceId}/config/resolve-drift")
    public ResponseEntity<ApplyConfigResponse> resolveDrift(
            @PathVariable Long deviceId,
            @RequestBody ResolveDriftRequest request) {
        return ResponseEntity.ok(deviceConfigQueryService.resolveDrift(deviceId, request));
    }
}