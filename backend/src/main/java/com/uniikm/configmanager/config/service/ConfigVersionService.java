package com.uniikm.configmanager.config.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.JsonDiff;
import com.uniikm.configmanager.config.model.ConfigVersion;
import com.uniikm.configmanager.config.repository.ConfigVersionRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConfigVersionService {
    private final ConfigVersionRepository configVersionRepository;
    private final DeviceConfigService deviceConfigService;
    private final ObjectMapper objectMapper;

    public ConfigVersion getLatestVersion() {
        return configVersionRepository.findTopByOrderByVersionNumDesc(); 
    }

    @Transactional
    public ConfigVersion createNewVersion(JsonNode newConfig, ConfigVersion parentVersion) {
        String checksum = computeChecksum(newConfig);
        int versionNum = parentVersion != null ? parentVersion.getVersionNum() + 1 : 1;
        ConfigVersion newVersion = ConfigVersion.builder()
                .versionNum(versionNum)
                .configData(newConfig)
                .checksum(checksum)
                .parentVersion(parentVersion)
                .build();
        return configVersionRepository.save(newVersion);
    }

    @Transactional
    public void markAsApplied(Long configVersionId) {
        ConfigVersion version = configVersionRepository.findById(configVersionId)
                .orElseThrow(() -> new IllegalArgumentException("ConfigVersion not found: " + configVersionId));
        configVersionRepository.save(version);
    }

    public JsonNode computeDiff(JsonNode oldConfig, JsonNode newConfig) {
        return JsonDiff.asJson(oldConfig, newConfig);
    }

    public String computeChecksum(JsonNode config) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(config);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute checksum", e);
        }
    }

    public ConfigVersion getVersionById(Long id) {
            return configVersionRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("ConfigVersion not found: " + id));
    }

    public List<ConfigVersion> getVersionHistoryByDeviceId(Long deviceId) {
        Optional<ConfigVersion> activeVersionOpt = deviceConfigService.getActiveVersion(deviceId);
        if (activeVersionOpt.isEmpty()) {
            return List.of(); 
        }
        List<ConfigVersion> history = new ArrayList<>();
        ConfigVersion current = activeVersionOpt.get();
        while (current != null) {
            history.add(current);
            current = current.getParentVersion();
        }
        
        //Collections.reverse(history); // чтобы первая была самая старая
        return history;
    }

    // Метод для сравнения двух версий (возвращает JSON patch)
    public JsonNode compareVersions(Long versionIdA, Long versionIdB) {
        ConfigVersion versionA = getVersionById(versionIdA);
        ConfigVersion versionB = getVersionById(versionIdB);
        return JsonDiff.asJson(versionA.getConfigData(), versionB.getConfigData());
    }
}