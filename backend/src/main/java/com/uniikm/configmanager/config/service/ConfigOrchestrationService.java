package com.uniikm.configmanager.config.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.uniikm.configmanager.common.dto.ConnectionProtocol;
import com.uniikm.configmanager.common.dto.DeviceCommandTarget;
import com.uniikm.configmanager.config.command.LinuxConfigCommandGenerator;
import com.uniikm.configmanager.config.command.WindowsConfigCommandGenerator;
import com.uniikm.configmanager.config.dto.ApplyConfigResponse;
import com.uniikm.configmanager.config.dto.DeviceCredentials;
import com.uniikm.configmanager.config.event.ConfigApplyEvent;
import com.uniikm.configmanager.config.model.ConfigVersion;
import com.uniikm.configmanager.device.enums.DeviceType;
import com.uniikm.configmanager.device.model.DeviceInfo;
import com.uniikm.configmanager.integration.dto.CommandExecutionRequest;
import com.uniikm.configmanager.integration.server.RemoteCommandService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigOrchestrationService {

    private final ConfigVersionService configVersionService;
    private final DeviceConfigService deviceConfigService;
    private final WindowsConfigCommandGenerator windowsCommandGenerator;
    private final LinuxConfigCommandGenerator linuxCommandGenerator;
    private final RemoteCommandService remoteCommandService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public ApplyConfigResponse applyConfiguration(List<DeviceInfo> devices, JsonNode newConfig,
                                       Map<Long, DeviceCredentials> credentialsMap) {
        
        String batchId = UUID.randomUUID().toString();
        List<String> groupTaskIds = new ArrayList<>();
        for (DeviceInfo device : devices) {
            DeviceCredentials creds = credentialsMap != null ? credentialsMap.get(device.getId()) : null;
            if (creds == null) {
                log.warn("Нет учётных данных для устройства {}", device.getId());
                continue;
            }
            String groupId = applyToSingleDevice(device, newConfig, creds);
            if (groupId != null) groupTaskIds.add(groupId);
        }
        redisTemplate.opsForList().rightPushAll("config:batch" + batchId, groupTaskIds);
        redisTemplate.expire("config:batch" + batchId, Duration.ofHours(1));
        return new ApplyConfigResponse(batchId, groupTaskIds);
    }

    private String applyToSingleDevice(DeviceInfo device, JsonNode newConfig, DeviceCredentials creds) {
        Long deviceId = device.getId();

        // 1. Активная версия
        Optional<ConfigVersion> activeVersionOpt = deviceConfigService.getActiveVersion(deviceId);
        // 2. Проверка изменений
        String newChecksum = configVersionService.computeChecksum(newConfig);
        if (activeVersionOpt.isPresent() && activeVersionOpt.get().getChecksum().equals(newChecksum)) {
            log.info("No changes for device {}", deviceId);
            return null;
        }

        // 3. Сохраняем новую версию
        ConfigVersion newVersion = configVersionService.createNewVersion(newConfig, activeVersionOpt.orElse(null));

        // 4. Генерируем команду
        String command;
        if (device.getType() == DeviceType.WINDOWS) {
            command = windowsCommandGenerator.generateCommand(newConfig); // внедрите бин WindowsConfigCommandGenerator
        } else {
            command = linuxCommandGenerator.generateCommand(newConfig);
        }

        DeviceCommandTarget target = buildDeviceCommandTarget(device, creds);
        CommandExecutionRequest execRequest = new CommandExecutionRequest(command, List.of(target), null);

        // 5. Асинхронный запуск
        var groupStatus = remoteCommandService.executeAsync(execRequest);
        String groupTaskId = groupStatus.taskGroupId();

        // 6. Redis: оперативный статус
        String redisKey = "config:apply:" + groupTaskId;
        redisTemplate.opsForHash().putAll(redisKey, Map.of(
            "deviceId", deviceId,
            "configVersionId", newVersion.getId(),
            "status", "IN_PROGRESS",
            "startedAt", Instant.now().toString()
        ));
        redisTemplate.expire(redisKey, Duration.ofHours(1));
        redisTemplate.opsForValue().set("device:current-task:" + deviceId, groupTaskId, Duration.ofHours(1));

        // 7. Аудит: начало применения
        eventPublisher.publishEvent(new ConfigApplyEvent(
            "CONFIG_APPLY_STARTED",
            "CONFIG",
            newVersion.getId(),
            null,
            Map.of("deviceId", deviceId, "groupTaskId", groupTaskId)
        ));

        return groupTaskId;
    }

    private DeviceCommandTarget buildDeviceCommandTarget(DeviceInfo device, DeviceCredentials creds) {
        String ip = device.getIps().isEmpty() ? device.getHostname() : device.getIps().get(0).getIp();
        ConnectionProtocol protocol = device.getType() == DeviceType.WINDOWS ? ConnectionProtocol.WINRM : ConnectionProtocol.SSH;
        return new DeviceCommandTarget(
            ip,
            creds.getPort(),     // Integer, может быть null
            creds.getUsername(),
            creds.getPassword(),
            protocol,
            null, null, null
        );
    }
}