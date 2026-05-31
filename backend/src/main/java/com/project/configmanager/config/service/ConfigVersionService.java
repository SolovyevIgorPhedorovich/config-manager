package com.project.configmanager.config.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.JsonDiff;
import com.project.configmanager.config.model.ConfigVersion;
import com.project.configmanager.config.repository.ConfigVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class ConfigVersionService {
    private final ConfigVersionRepository configVersionRepository;
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
}