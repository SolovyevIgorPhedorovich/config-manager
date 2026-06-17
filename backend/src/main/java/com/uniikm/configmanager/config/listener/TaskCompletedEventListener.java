package com.uniikm.configmanager.config.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.uniikm.configmanager.cache.ConfigApplyStatusStore;
import com.uniikm.configmanager.config.event.TaskCompletedEvent;
import com.uniikm.configmanager.config.model.ConfigVersion;
import com.uniikm.configmanager.config.service.ConfigVersionService;
import com.uniikm.configmanager.config.service.DeviceConfigService;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskCompletedEventListener {

    private final ConfigApplyStatusStore applyStatusStore;
    private final DeviceConfigService deviceConfigService;
    private final ConfigVersionService configVersionService;

    @EventListener
    @Transactional
    public void handleTaskCompleted(TaskCompletedEvent event) {
        String groupTaskId = event.getGroupTaskId();
        boolean success = "SUCCESS".equals(event.getStatus());

        log.info("Task completed: groupTaskId={}, status={}", groupTaskId, event.getStatus());

        applyStatusStore.completeApplyStatus(groupTaskId, event.getStatus(), event.getError());

        Long deviceId = applyStatusStore.getDeviceId(groupTaskId);
        Long configVersionId = applyStatusStore.getConfigVersionId(groupTaskId);

        if (deviceId == null || configVersionId == null) {
            log.warn("Incomplete data in Redis for groupTaskId={}", groupTaskId);
            return;
        }

        if (success) {
            ConfigVersion version = configVersionService.getVersionById(configVersionId);
            deviceConfigService.setActiveConfig(deviceId, version);
            log.info("Active config updated for device {} to version {}", deviceId, configVersionId);
        }

        applyStatusStore.clearCurrentTask(deviceId);

        log.info("Status updated in Redis for device {}: {}", deviceId, event.getStatus());
    }
}
