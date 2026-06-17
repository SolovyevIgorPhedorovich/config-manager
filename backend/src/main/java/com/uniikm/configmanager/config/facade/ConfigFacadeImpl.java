package com.uniikm.configmanager.config.facade;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.JsonDiff;
import com.uniikm.configmanager.cache.ConfigApplyStatusStore;
import com.uniikm.configmanager.config.dto.ApplyConfigRequest;
import com.uniikm.configmanager.config.dto.ApplyConfigResponse;
import com.uniikm.configmanager.config.dto.ConfigCompareRequest;
import com.uniikm.configmanager.config.dto.ConfigCompareResponse;
import com.uniikm.configmanager.config.dto.ConfigHistoryEntry;
import com.uniikm.configmanager.config.dto.ConfigHistoryResponse;
import com.uniikm.configmanager.config.dto.ConfigStatusResponse;
import com.uniikm.configmanager.config.model.ConfigVersion;
import com.uniikm.configmanager.config.service.ConfigOrchestrationService;
import com.uniikm.configmanager.config.service.ConfigVersionService;
import com.uniikm.configmanager.config.service.DeviceConfigCaptureService;
import com.uniikm.configmanager.config.service.DeviceConfigService;
import com.uniikm.configmanager.device.facade.DeviceFacade;
import com.uniikm.configmanager.integration.dto.DeviceProbeResult;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ConfigFacadeImpl implements ConfigFacade {
    private final DeviceFacade deviceFacade;
    private final ConfigOrchestrationService orchestrationService;
    private final DeviceConfigCaptureService deviceConfigCaptureService;
    private final ObjectMapper objectMapper;
    private final ConfigVersionService configVersionService;
    private final ConfigApplyStatusStore applyStatusStore;

    @Override
    public ApplyConfigResponse apply(ApplyConfigRequest request) {
        var devices = deviceFacade.getDeviceEntities(request.getDeviceIds());
        JsonNode configNode = objectMapper.valueToTree(request.getConfigData());
        return orchestrationService.applyConfiguration(
            devices, 
            configNode,
            request.getDeviceCredentials()  // новая карта
        );
    }

    @Override
    public ConfigHistoryResponse getHistory(Long deviceId) {
       List<ConfigVersion> versions = configVersionService.getVersionHistoryByDeviceId(deviceId);
       List<ConfigHistoryEntry> entries = versions.stream()
                .map(v -> new ConfigHistoryEntry(
                        v.getId(),
                        v.getVersionNum(),
                        v.getCreatedAt(),
                        v.getChecksum(),
                        v.getParentVersion() != null ? v.getParentVersion().getId() : null,
                        v.getConfigData()
                ))
                .collect(Collectors.toList());
        return new ConfigHistoryResponse(deviceId, entries);
    }

    @Override
    public ConfigCompareResponse compare(ConfigCompareRequest request) {
        JsonNode diff = configVersionService.compareVersions(
        request.newVersionId(), 
        request.oldVersionId()
        );
        ConfigVersion versionA = configVersionService.getVersionById(request.newVersionId());
        ConfigVersion versionB = configVersionService.getVersionById(request.oldVersionId());
        return new ConfigCompareResponse(
            request.newVersionId(),
            request.oldVersionId(),
            diff,
            versionA.getChecksum(),
            versionB.getChecksum()
        );
    }

    @Override
    public ConfigStatusResponse getApplyGroupStatus(String groupTaskId) {
        return applyStatusStore.getApplyStatus(groupTaskId)
                .map(v -> new ConfigStatusResponse(
                        groupTaskId, v.status(), v.deviceId(), v.startedAt(), v.finishedAt(), v.errorMessage()))
                // Ключ ещё не создан или истёк — считаем статус неизвестным
                .orElse(new ConfigStatusResponse(groupTaskId, "UNKNOWN", null, null, null, null));
    }

    @Override
    public List<ConfigStatusResponse> getStatus(String batchId) {
        List<Object> taskGroupIds = applyStatusStore.getBatchTaskIds(batchId);

        if (taskGroupIds == null || taskGroupIds.isEmpty()) {
            throw new IllegalArgumentException("Batch not found: " + batchId);
        }

        return taskGroupIds.stream()
                .map(Object::toString)
                .map(this::getApplyGroupStatus)
                .collect(Collectors.toList());
    }

    @Override
    public void captureAndReconcile(Long deviceId, DeviceProbeResult probe) {
        deviceConfigCaptureService.captureAndReconcile(deviceId, probe);
    }

}
