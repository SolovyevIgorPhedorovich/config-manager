package com.uniikm.configmanager.config.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uniikm.configmanager.config.model.ConfigVersion;
import com.uniikm.configmanager.device.model.DeviceInfo;
import com.uniikm.configmanager.device.repository.DeviceRepository;
import com.uniikm.configmanager.integration.dto.DeviceProbeResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Захват фактической конфигурации устройства из данных сетевого опроса и
 * детектирование расхождений (drift) с сохранённой активной версией.
 *
 * <p>Сравнение ведётся по наблюдаемому управляемому подмножеству полей (сейчас —
 * {@code hostname}, который отдаёт любой протокол опроса SNMP/SSH/WinRM). Это
 * сознательно консервативно: сравнивается «намеренная» конфигурация (активная
 * версия) с «фактической» (из опроса), без хрупкого обратного разбора CLI и без
 * ложных срабатываний при наличии богатых применённых конфигов. Набор полей
 * расширяем — см. {@link #projectForDrift}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceConfigCaptureService {

    private final ObjectMapper objectMapper;
    private final ConfigVersionService configVersionService;
    private final DeviceConfigService deviceConfigService;
    private final DeviceRepository deviceRepository;

    /**
     * Сверяет фактическое состояние устройства (из опроса) с сохранённой активной
     * версией. Если активной версии нет — создаёт базовую (baseline) из факта.
     * Если фактический hostname разошёлся с активной версией — помечает устройство
     * флагом drift и сохраняет версию-факт (для последующего разрешения конфликта).
     */
    @Transactional
    public void captureAndReconcile(Long deviceId, DeviceProbeResult probe) {
        DeviceInfo device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null) return;

        ObjectNode actual = buildCanonicalConfig(device, probe);
        Optional<ConfigVersion> activeOpt = deviceConfigService.getActiveVersion(deviceId);

        if (activeOpt.isEmpty()) {
            // Базовый снимок: фиксируем фактическую конфигурацию как активную версию.
            ConfigVersion baseline = configVersionService.createNewVersion(actual, null);
            deviceConfigService.setActiveConfig(deviceId, baseline);
            clearDrift(device);
            log.info("Захвачена базовая конфигурация устройства {} (версия {})", deviceId, baseline.getId());
            return;
        }

        ConfigVersion active = activeOpt.get();
        String actualKey = configVersionService.computeChecksum(projectForDrift(actual));
        String activeKey = configVersionService.computeChecksum(projectForDrift(active.getConfigData()));

        if (actualKey.equals(activeKey)) {
            clearDrift(device); // фактическое состояние совпадает с сохранённым
            return;
        }

        // Расхождение: сохраняем версию-факт (не активную) и помечаем устройство.
        ConfigVersion driftVersion = configVersionService.createNewVersion(actual, active);
        device.setConfigDrift(true);
        device.setDriftVersionId(driftVersion.getId());
        deviceRepository.save(device);
        log.info("Обнаружено расхождение конфигурации устройства {}: активная v{} ≠ факт (версия-факт {})",
                deviceId, active.getVersionNum(), driftVersion.getId());
    }

    private void clearDrift(DeviceInfo device) {
        if (Boolean.TRUE.equals(device.getConfigDrift()) || device.getDriftVersionId() != null) {
            device.setConfigDrift(false);
            device.setDriftVersionId(null);
            deviceRepository.save(device);
        }
    }

    /**
     * Канонический снимок фактической конфигурации из данных опроса.
     * Содержит стабильные управляемые поля (без счётчиков/таймстампов).
     */
    private ObjectNode buildCanonicalConfig(DeviceInfo device, DeviceProbeResult probe) {
        ObjectNode node = objectMapper.createObjectNode();

        // hostname: имя из опроса, но не «голый IP» (PORT-детект отдаёт hostname == ip)
        String probeHost = probe != null ? probe.hostname() : null;
        boolean hostIsJustIp = probeHost != null && probe != null && probeHost.equals(probe.ip());
        String hostname = (probeHost != null && !probeHost.isBlank() && !hostIsJustIp)
                ? probeHost : device.getHostname();
        node.put("hostname", hostname);

        String ip = device.getIps().isEmpty() ? null : device.getIps().get(0).getIp();
        if (ip != null) node.put("ipAddress", ip);

        if (probe != null) {
            if (probe.sysLocation() != null && !probe.sysLocation().isBlank())
                node.put("sysLocation", probe.sysLocation().trim());
            if (probe.sysContact() != null && !probe.sysContact().isBlank())
                node.put("sysContact", probe.sysContact().trim());
        }
        return node;
    }

    /**
     * Проекция на наблюдаемое подмножество, по которому сравнивается drift.
     * Сейчас — только hostname (универсально наблюдаем по всем протоколам и
     * присутствует во всех схемах редакторов). Порядок ключей фиксирован для
     * детерминированной контрольной суммы.
     */
    private ObjectNode projectForDrift(JsonNode config) {
        ObjectNode out = objectMapper.createObjectNode();
        if (config != null && config.hasNonNull("hostname")) {
            out.put("hostname", config.get("hostname").asText("").trim());
        } else {
            out.put("hostname", "");
        }
        return out;
    }
}
