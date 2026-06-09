package com.uniikm.configmanager.config.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.uniikm.configmanager.config.dto.ApplyConfigResponse;
import com.uniikm.configmanager.config.dto.DeviceCredentials;
import com.uniikm.configmanager.config.dto.DriftComparisonResponse;
import com.uniikm.configmanager.config.dto.ResolveDriftRequest;
import com.uniikm.configmanager.config.model.ConfigVersion;
import com.uniikm.configmanager.device.model.DeviceInfo;
import com.uniikm.configmanager.device.repository.DeviceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Операции над конфигурацией конкретного устройства для интерфейса:
 * чтение активной версии (префилл редактора), разрешение расхождений (drift),
 * получение конфигурации в виде параметризованного шаблона.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceConfigQueryService {

    private final DeviceConfigService deviceConfigService;
    private final ConfigVersionService configVersionService;
    private final DeviceRepository deviceRepository;
    private final ConfigOrchestrationService orchestrationService;
    private final TemplateParameterizer templateParameterizer;

    /** Активная (текущая) конфигурация устройства — для предзаполнения редактора. */
    @Transactional(readOnly = true)
    public JsonNode getActiveConfig(Long deviceId) {
        return deviceConfigService.getActiveVersion(deviceId)
                .map(ConfigVersion::getConfigData)
                .orElse(null);
    }

    /** Сравнение сохранённой и фактической конфигураций для разрешения расхождения. */
    @Transactional(readOnly = true)
    public DriftComparisonResponse getDriftComparison(Long deviceId) {
        DeviceInfo device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Устройство не найдено: " + deviceId));
        if (!Boolean.TRUE.equals(device.getConfigDrift()) || device.getDriftVersionId() == null) {
            throw new IllegalStateException("У устройства нет зафиксированного расхождения конфигурации");
        }
        JsonNode stored = getActiveConfig(deviceId);
        JsonNode actual = configVersionService.getVersionById(device.getDriftVersionId()).getConfigData();
        return new DriftComparisonResponse(stored, actual);
    }

    /** Активная конфигурация, преобразованная в шаблон (уникальные поля → переменные). */
    @Transactional(readOnly = true)
    public JsonNode getConfigAsTemplate(Long deviceId) {
        JsonNode active = getActiveConfig(deviceId);
        if (active == null) {
            throw new IllegalArgumentException(
                    "У устройства " + deviceId + " нет сохранённой конфигурации для создания шаблона");
        }
        return templateParameterizer.parameterize(active);
    }

    /** Разрешение расхождения конфигурации. */
    @Transactional
    public ApplyConfigResponse resolveDrift(Long deviceId, ResolveDriftRequest req) {
        DeviceInfo device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Устройство не найдено: " + deviceId));

        if (!Boolean.TRUE.equals(device.getConfigDrift()) || device.getDriftVersionId() == null) {
            throw new IllegalStateException("У устройства нет зафиксированного расхождения конфигурации");
        }

        String resolution = req.getResolution() == null ? "" : req.getResolution().trim().toUpperCase();
        return switch (resolution) {
            case "ACCEPT_DEVICE" -> acceptDevice(device);
            case "REAPPLY_STORED" -> reapplyStored(device, req.getCredentials());
            default -> throw new IllegalArgumentException(
                    "Неизвестное разрешение: '" + req.getResolution() + "'. Ожидается ACCEPT_DEVICE или REAPPLY_STORED");
        };
    }

    /** Принять фактическую конфигурацию устройства как активную. */
    private ApplyConfigResponse acceptDevice(DeviceInfo device) {
        ConfigVersion driftVersion = configVersionService.getVersionById(device.getDriftVersionId());
        deviceConfigService.setActiveConfig(device.getId(), driftVersion);
        clearDrift(device);
        log.info("Drift устройства {}: принята фактическая конфигурация (версия {})",
                device.getId(), driftVersion.getId());
        return new ApplyConfigResponse(null, List.of(), List.of());
    }

    /** Повторно применить сохранённую активную конфигурацию на устройство. */
    private ApplyConfigResponse reapplyStored(DeviceInfo device, DeviceCredentials creds) {
        Optional<ConfigVersion> activeOpt = deviceConfigService.getActiveVersion(device.getId());
        if (activeOpt.isEmpty()) {
            throw new IllegalStateException("У устройства нет активной версии для повторного применения");
        }
        if (creds == null) {
            throw new IllegalArgumentException("Для повторного применения нужны учётные данные устройства");
        }
        ApplyConfigResponse response = orchestrationService.applyVersion(
                device, activeOpt.get(), creds, "DRIFT_REAPPLY");
        // Сбрасываем флаг расхождения: при неудаче следующий скан обнаружит его заново.
        clearDrift(device);
        log.info("Drift устройства {}: повторно применяется сохранённая конфигурация (taskGroups={}, scheduled={})",
                device.getId(), response.taskGroupIds(), response.scheduledDeviceIds());
        return response;
    }

    private void clearDrift(DeviceInfo device) {
        device.setConfigDrift(false);
        device.setDriftVersionId(null);
        deviceRepository.save(device);
    }
}
