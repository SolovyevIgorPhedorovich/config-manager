package com.uniikm.configmanager.config.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.uniikm.configmanager.audit.enums.AuditAction;
import com.uniikm.configmanager.config.model.ConfigVersion;
import com.uniikm.configmanager.config.repository.ConfigVersionRepository;
import com.uniikm.configmanager.config.service.ConfigVersionService;
import com.uniikm.configmanager.device.events.DeviceEvent;
import com.uniikm.configmanager.device.model.DeviceInfo;

import lombok.RequiredArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConfigEventListener {

    private final ConfigVersionService configVersionService;
    private final ConfigVersionRepository configVersionRepository;

    @EventListener
    public void handle(DeviceEvent event) {

        if (event.action() != AuditAction.CONFIG_APPLIED) {
            return;
        }

        DeviceInfo device = event.device();

        Integer nextVersion = 1;

        ConfigVersion lastVersion = configVersionService.getLatestVersion();

        if (lastVersion != null) {
            nextVersion = lastVersion.getVersionNum() + 1;
        }

        ConfigVersion version = ConfigVersion.builder()
            .versionNum(nextVersion)
            .configData((JsonNode) event.afterState())
            .checksum(calculateChecksum(event.afterState().toString()))
            .parentVersion(lastVersion)
            .build();

        configVersionRepository.save(version);
    }

    public String calculateChecksum(String data) {
    try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));

        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }

        return hex.toString();

    } catch (Exception e) {
        throw new RuntimeException("Checksum error", e);
    }
}
}