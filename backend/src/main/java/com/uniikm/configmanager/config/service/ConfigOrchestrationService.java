package com.uniikm.configmanager.config.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.uniikm.configmanager.common.dto.ConnectionProtocol;
import com.uniikm.configmanager.common.dto.DeviceCommandTarget;
import com.uniikm.configmanager.config.command.CiscoConfigCommandGenerator;
import com.uniikm.configmanager.config.command.LinuxConfigCommandGenerator;
import com.uniikm.configmanager.config.command.MFUConfigCommandGenerator;
import com.uniikm.configmanager.config.command.ProxmoxConfigCommandGenerator;
import com.uniikm.configmanager.config.command.WindowsConfigCommandGenerator;
import com.uniikm.configmanager.config.dto.ApplyConfigResponse;
import com.uniikm.configmanager.config.dto.DeviceCredentials;
import com.uniikm.configmanager.config.event.ConfigApplyEvent;
import com.uniikm.configmanager.config.model.ConfigVersion;
import com.uniikm.configmanager.device.model.DeviceInfo;
import com.uniikm.configmanager.integration.dto.CommandExecutionRequest;
import com.uniikm.configmanager.integration.facade.IntegrationFacade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
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
    private final CiscoConfigCommandGenerator ciscoCommandGenerator;
    private final ProxmoxConfigCommandGenerator proxmoxCommandGenerator;
    private final MFUConfigCommandGenerator mfuCommandGenerator;
    private final IntegrationFacade integrationFacade;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    // Очередь отложенного применения. @Lazy разрывает циклическую зависимость:
    // поллер очереди вызывает executeApply() этого сервиса.
    @Autowired
    @Lazy
    private ScheduledConfigApplyService scheduledConfigApplyService;

    public ApplyConfigResponse applyConfiguration(List<DeviceInfo> devices, JsonNode newConfig,
                                       Map<Long, DeviceCredentials> credentialsMap) {
        return applyConfiguration(devices, newConfig, credentialsMap, "CONFIG");
    }

    public ApplyConfigResponse applyConfiguration(List<DeviceInfo> devices, JsonNode newConfig,
                                       Map<Long, DeviceCredentials> credentialsMap, String source) {

        String batchId = UUID.randomUUID().toString();
        List<String> groupTaskIds = new ArrayList<>();
        List<Long> scheduledDeviceIds = new ArrayList<>();
        for (DeviceInfo device : devices) {
            DeviceCredentials creds = credentialsMap != null ? credentialsMap.get(device.getId()) : null;
            if (creds == null) {
                log.warn("Нет учётных данных для устройства {}", device.getId());
                continue;
            }
            SingleApplyResult result = applyToSingleDevice(device, newConfig, creds, source, batchId);
            if (result.groupTaskId() != null) groupTaskIds.add(result.groupTaskId());
            if (result.scheduled()) scheduledDeviceIds.add(device.getId());
        }
        redisTemplate.opsForList().rightPushAll("config:batch" + batchId, groupTaskIds);
        redisTemplate.expire("config:batch" + batchId, Duration.ofHours(1));
        return new ApplyConfigResponse(batchId, groupTaskIds, scheduledDeviceIds);
    }

    /** Результат применения к одному устройству: либо запущено (groupTaskId), либо отложено (scheduled). */
    private record SingleApplyResult(String groupTaskId, boolean scheduled) {
        static SingleApplyResult executed(String id) { return new SingleApplyResult(id, false); }
        static SingleApplyResult deferred()          { return new SingleApplyResult(null, true); }
        static SingleApplyResult noop()              { return new SingleApplyResult(null, false); }
    }

    private SingleApplyResult applyToSingleDevice(DeviceInfo device, JsonNode newConfig,
                                                  DeviceCredentials creds, String source, String batchId) {
        Long deviceId = device.getId();

        // 1. Активная версия + проверка изменений
        Optional<ConfigVersion> activeVersionOpt = deviceConfigService.getActiveVersion(deviceId);
        String newChecksum = configVersionService.computeChecksum(newConfig);
        if (activeVersionOpt.isPresent() && activeVersionOpt.get().getChecksum().equals(newChecksum)) {
            log.info("No changes for device {}", deviceId);
            return SingleApplyResult.noop();
        }

        // 2. Сохраняем новую версию
        ConfigVersion newVersion = configVersionService.createNewVersion(newConfig, activeVersionOpt.orElse(null));

        // 3. Гейт по доступности: офлайн-устройство → в очередь отложенного применения
        String ip = primaryIp(device);
        if (!integrationFacade.isReachable(ip)) {
            scheduledConfigApplyService.enqueue(device, newVersion, creds, source, batchId);
            log.info("Устройство {} ({}) офлайн — применение версии {} запланировано",
                    deviceId, ip, newVersion.getId());
            eventPublisher.publishEvent(new ConfigApplyEvent(
                "CONFIG_APPLY_SCHEDULED",
                "CONFIG",
                newVersion.getId(),
                null,
                Map.of("deviceId", deviceId, "reason", "device offline")
            ));
            return SingleApplyResult.deferred();
        }

        // 4. Устройство в сети — запускаем применение немедленно
        String groupTaskId = executeApply(device, newVersion, creds);
        return SingleApplyResult.executed(groupTaskId);
    }

    /**
     * Явно применяет заданную версию конфигурации (без проверки «нет изменений»):
     * онлайн — немедленно, офлайн — в очередь отложенного применения.
     * Используется для повторного применения сохранённой конфигурации при
     * разрешении расхождения (drift).
     */
    public ApplyConfigResponse applyVersion(DeviceInfo device, ConfigVersion version,
                                            DeviceCredentials creds, String source) {
        String ip = primaryIp(device);
        if (!integrationFacade.isReachable(ip)) {
            scheduledConfigApplyService.enqueue(device, version, creds, source, null);
            return new ApplyConfigResponse(null, List.of(), List.of(device.getId()));
        }
        String groupTaskId = executeApply(device, version, creds);
        return new ApplyConfigResponse(null, List.of(groupTaskId), List.of());
    }

    /**
     * Запускает применение конкретной версии конфигурации к устройству:
     * генерирует команду, фиксирует статус в Redis и запускает асинхронную задачу.
     * Используется как при немедленном применении, так и фоновым поллером очереди.
     */
    public String executeApply(DeviceInfo device, ConfigVersion version, DeviceCredentials creds) {
        Long deviceId = device.getId();
        JsonNode config = version.getConfigData();

        // Генерируем команду в зависимости от типа устройства (для ПК — по ОС).
        // Для Linux/Proxmox команды требуют root — оборачиваем в подъём привилегий
        // через sudo с тем же паролем, что и для SSH-входа.
        String command;
        boolean elevate = false;
        switch (device.getType()) {
            case PC -> {
                if (device.isWindows()) {
                    command = windowsCommandGenerator.generateCommand(config);
                } else {
                    command = linuxCommandGenerator.generateCommand(config);
                    elevate = true;
                }
            }
            case CISCO   -> command = ciscoCommandGenerator.generateCommand(config);
            case PROXMOX -> { command = proxmoxCommandGenerator.generateCommand(config); elevate = true; }
            case МФУ     -> command = mfuCommandGenerator.generateCommand(config);
            default      -> throw new IllegalStateException("Неизвестный тип устройства: " + device.getType());
        }
        if (elevate) {
            command = wrapWithSudo(command, creds.getPassword());
        }

        DeviceCommandTarget target = buildDeviceCommandTarget(device, creds);

        // Redis: записываем ДО запуска задачи, чтобы избежать race condition
        String groupTaskId = UUID.randomUUID().toString();
        String redisKey = "config:apply:" + groupTaskId;
        redisTemplate.opsForHash().putAll(redisKey, Map.of(
            "deviceId", deviceId,
            "configVersionId", version.getId(),
            "status", "IN_PROGRESS",
            "startedAt", Instant.now().toString()
        ));
        redisTemplate.expire(redisKey, Duration.ofHours(1));
        redisTemplate.opsForValue().set("device:current-task:" + deviceId, groupTaskId, Duration.ofHours(1));

        CommandExecutionRequest execRequest = new CommandExecutionRequest(command, List.of(target), null, groupTaskId);

        integrationFacade.executeAsync(execRequest);

        eventPublisher.publishEvent(new ConfigApplyEvent(
            "CONFIG_APPLY_STARTED",
            "CONFIG",
            version.getId(),
            null,
            Map.of("deviceId", deviceId, "groupTaskId", groupTaskId)
        ));

        return groupTaskId;
    }

    /**
     * Оборачивает bash-скрипт в подъём привилегий. Скрипт пишется во временный
     * файл (через quoted-heredoc, чтобы переменные внутри — {@code $CON}, {@code $PM},
     * {@code ${NODE_VER}} — раскрывал bash при выполнении, а не внешняя оболочка),
     * затем запускается под root:
     * <ul>
     *   <li>уже root (типично для Proxmox) — {@code bash <file>};</li>
     *   <li>иначе — {@code sudo -S -p '' bash <file>}, пароль sudo (тот же, что и
     *       для SSH-входа) подаётся в stdin sudo. Файл — это аргумент, поэтому
     *       конфликта между stdin sudo и содержимым скрипта нет.</li>
     * </ul>
     * Код возврата проксируется наружу, чтобы интерфейс видел реальный результат.
     */
    private String wrapWithSudo(String script, String password) {
        String pass = shellSingleQuote(password == null ? "" : password);
        return "__CFG_FILE__=$(mktemp)\n"
             + "cat > \"$__CFG_FILE__\" <<'__CFG_EOF__'\n"
             + script
             + "\n__CFG_EOF__\n"
             + "if [ \"$(id -u)\" -eq 0 ]; then\n"
             + "  bash \"$__CFG_FILE__\"; __CFG_RC__=$?\n"
             + "else\n"
             + "  echo " + pass + " | sudo -S -p '' bash \"$__CFG_FILE__\"; __CFG_RC__=$?\n"
             + "fi\n"
             + "rm -f \"$__CFG_FILE__\"\n"
             + "exit $__CFG_RC__\n";
    }

    /** Безопасно заключает значение в одинарные кавычки для bash. */
    private String shellSingleQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private String primaryIp(DeviceInfo device) {
        return device.getIps().isEmpty() ? device.getHostname() : device.getIps().get(0).getIp();
    }

    private DeviceCommandTarget buildDeviceCommandTarget(DeviceInfo device, DeviceCredentials creds) {
        String ip = primaryIp(device);
        ConnectionProtocol protocol = switch (device.getType()) {
            case PC  -> device.isWindows() ? ConnectionProtocol.WINRM : ConnectionProtocol.SSH;
            case МФУ -> ConnectionProtocol.SNMP;
            default  -> ConnectionProtocol.SSH; // CISCO, PROXMOX
        };
        String community = (creds.getCommunity() != null && !creds.getCommunity().isBlank())
                ? creds.getCommunity() : "private";
        return new DeviceCommandTarget(
            ip,
            creds.getPort(),
            creds.getUsername(),
            creds.getPassword(),
            protocol,
            community, null, null
        );
    }
}
