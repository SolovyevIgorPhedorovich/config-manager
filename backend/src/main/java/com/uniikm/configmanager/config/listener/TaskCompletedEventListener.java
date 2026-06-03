package com.uniikm.configmanager.config.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.uniikm.configmanager.config.event.TaskCompletedEvent;
import com.uniikm.configmanager.config.model.ConfigVersion;
import com.uniikm.configmanager.config.service.ConfigVersionService;
import com.uniikm.configmanager.config.service.DeviceConfigService;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskCompletedEventListener {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DeviceConfigService deviceConfigService;
    private final ConfigVersionService configVersionService;

    @EventListener
    @Transactional
    public void handleTaskCompleted(TaskCompletedEvent event) {
        String groupTaskId = event.getGroupTaskId();
        boolean success = "SUCCESS".equals(event.getStatus());

        log.info("Task completed: groupTaskId={}, status={}", groupTaskId, event.getStatus());

        String redisKey = "config:apply:" + groupTaskId;
        redisTemplate.opsForHash().put(redisKey, "status", event.getStatus());
        redisTemplate.opsForHash().put(redisKey, "finishedAt", Instant.now().toString());
        if (event.getError() != null) {
            redisTemplate.opsForHash().put(redisKey, "errorMessage", event.getError());
        }

        Long deviceId = extractLong(redisTemplate.opsForHash().get(redisKey, "deviceId"));
        Long configVersionId = extractLong(redisTemplate.opsForHash().get(redisKey, "configVersionId"));

        if (deviceId == null || configVersionId == null) {
            log.warn("Incomplete data in Redis for groupTaskId={}", groupTaskId);
            return;
        }

        if (success) {
            ConfigVersion version = configVersionService.getVersionById(configVersionId);
            deviceConfigService.setActiveConfig(deviceId, version);
            log.info("Active config updated for device {} to version {}", deviceId, configVersionId);
        }

        redisTemplate.delete("device:current-task:" + deviceId);
        
        log.info("Status updated in Redis for device {}: {}", deviceId, event.getStatus());
    }

    private Long extractLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}