package com.uniikm.configmanager.config.facade;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.JsonDiff;
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
import com.uniikm.configmanager.config.service.DeviceConfigService;
import com.uniikm.configmanager.device.facade.DeviceFacade;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ConfigFacadeImpl implements ConfigFacade {
    private final DeviceFacade deviceFacade;
    private final ConfigOrchestrationService orchestrationService;
    private final ObjectMapper objectMapper;
    private final ConfigVersionService configVersionService;
    private final RedisTemplate<String, Object> redisTemplate;

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
        Map<Object, Object> h = redisTemplate.opsForHash().entries("config:apply:" + groupTaskId);
        if (h == null || h.isEmpty()) {
            // Ключ ещё не создан или истёк — считаем статус неизвестным
            return new ConfigStatusResponse(groupTaskId, "UNKNOWN", null, null, null, null);
        }
        return new ConfigStatusResponse(
                groupTaskId,
                asStr(h.get("status")),
                asLong(h.get("deviceId")),
                asStr(h.get("startedAt")),
                asStr(h.get("finishedAt")),
                asStr(h.get("errorMessage"))
        );
    }

    private String asStr(Object o) {
        return o == null ? null : o.toString();
    }

    private Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.valueOf(o.toString()); } catch (NumberFormatException e) { return null; }
    }

    @Override
    public List<ConfigStatusResponse> getStatus(String batchId) {
        String key = "config:batch:" + batchId;

        List<Object> taskGroupIds =
                redisTemplate.opsForList().range(key, 0, -1);

        if (taskGroupIds == null || taskGroupIds.isEmpty()) {
            throw new IllegalArgumentException("Batch not found: " + batchId);
        }

        List<ConfigStatusResponse> statuses =
        taskGroupIds.stream()
                .map(Object::toString)
                .flatMap(id -> getStatus(id).stream())
                .collect(Collectors.toList());

        return statuses;
    }
    
}
