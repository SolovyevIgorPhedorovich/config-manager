package com.project.configmanager.config.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.configmanager.config.model.ConfigVersion;
import com.project.configmanager.config.model.DeviceConfig;
import com.project.configmanager.config.repository.DeviceConfigRepository;
import com.project.configmanager.config.service.DeviceConfigService.ConfigureResult;
import com.project.configmanager.device.model.DeviceInfo;
import com.project.configmanager.device.repository.DeviceRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceConfigService {

    private final DeviceRepository deviceRepository;
    private final ObjectMapper objectMapper;
    private final DeviceConfigRepository deviceConfigRepository;

    @Transactional
    public void setActiveConfig(Long deviceId, ConfigVersion configVersion) {
        DeviceConfig active = deviceConfigRepository.findByDeviceId(deviceId)
                .orElse(new DeviceConfig());
        active.setDeviceId(deviceId);
        active.setConfigVersion(configVersion);
        active.setAppliedAt(LocalDateTime.now());
        deviceConfigRepository.save(active);
    }

    public Optional<ConfigVersion> getActiveVersion(Long deviceId) {
        return (deviceConfigRepository.findByDeviceId(deviceId)
                .map(DeviceConfig::getConfigVersion));
    }

    public record ConfigureResult(boolean success, String message, Map<String, Object> details) {}

    public CompletableFuture<ConfigureResult> configureDevice(Long deviceId, String configJson) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                DeviceInfo device = deviceRepository.findById(deviceId)
                    .orElseThrow(() -> new RuntimeException("Устройство не найдено"));

                log.info("Применение настроек для устройства: {} (тип: {})", 
                    device.getHostname(), device.getType());

                switch (device.getType()) {
                    case WINDOWS:
                        return configureWindows(device, configJson);
                    case CISCO:
                        return configureCisco(device, configJson);
                    case МФУ:
                        return configureMFU(device, configJson);
                    default:
                        return new ConfigureResult(false, "Неизвестный тип устройства", null);
                }
            } catch (Exception e) {
                log.error("Ошибка при применении настроек для устройства ID {}", deviceId, e);
                return new ConfigureResult(false, "Ошибка: " + e.getMessage(), null);
            }
        });
    }

    private ConfigureResult configureWindows(DeviceInfo device, String configJson) {
        try {
            Map<String, Object> config = objectMapper.readValue(configJson, Map.class);
            
            log.info("Применение PowerShell конфигурации для Windows: {}", device.getHostname());

            StringBuilder powershellScript = new StringBuilder();
            powershellScript.append("$config = ").append(convertToPowerShellObject(config)).append("\n");

            if (config.containsKey("hostname")) {
                String newHostname = config.get("hostname").toString();
                powershellScript.append("Rename-Computer -NewName \"")
                    .append(newHostname)
                    .append("\" -Force\n");
            }

            if (config.containsKey("network")) {
                Map<String, Object> network = (Map<String, Object>) config.get("network");
                if (network.containsKey("ipAddress") && network.containsKey("gateway")) {
                    String ip = network.get("ipAddress").toString();
                    String gateway = network.get("gateway").toString();
                    powershellScript.append("New-NetIPAddress -InterfaceAlias \"Ethernet\" ")
                        .append("-IPAddress \"").append(ip).append("\" ")
                        .append("-DefaultGateway \"").append(gateway).append("\" -PrefixLength 24\n");
                }
            }

            if (config.containsKey("domain")) {
                Map<String, Object> domain = (Map<String, Object>) config.get("domain");
                String domainName = domain.get("name").toString();
                powershellScript.append("Add-Computer -DomainName \"")
                    .append(domainName)
                    .append("\" -Credential $cred -Force\n");
            }

            powershellScript.append("Write-Host \"Конфигурация применена успешно\"");
            
            String script = powershellScript.toString();
            log.info("Сгенерированный PowerShell скрипт:\n{}", script);

            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[PowerShell] {}", line);
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                return new ConfigureResult(
                    true, 
                    "Конфигурация Windows применена успешно", 
                    Map.of("hostname", device.getHostname(), "exitCode", exitCode)
                );
            } else {
                return new ConfigureResult(
                    false, 
                    "Ошибка применения конфигурации Windows (exit code: " + exitCode + ")", 
                    Map.of("hostname", device.getHostname(), "output", output.toString())
                );
            }
        } catch (Exception e) {
            log.error("Ошибка при применении конфигурации Windows", e);
            return new ConfigureResult(false, "Ошибка: " + e.getMessage(), null);
        }
    }

    private String convertToPowerShellObject(Map<String, Object> config) {
        StringBuilder sb = new StringBuilder("@{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            if (!first) sb.append(" ");
            first = false;
            sb.append(entry.getKey()).append(" = ");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("'").append(value).append("'");
            } else if (value instanceof Map) {
                sb.append(convertToPowerShellObject((Map<String, Object>) value));
            } else {
                sb.append(value);
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private ConfigureResult configureCisco(DeviceInfo device, String configJson) {
        try {
            Map<String, Object> config = objectMapper.readValue(configJson, Map.class);
            
            log.info("Применение конфигурации через SSH для Cisco: {}", device.getHostname());

            StringBuilder ciscoConfig = new StringBuilder();
            ciscoConfig.append("configure terminal\n");

            if (config.containsKey("hostname")) {
                ciscoConfig.append("hostname ").append(config.get("hostname")).append("\n");
            }

            if (config.containsKey("interfaces")) {
                Map<String, Object> interfaces = (Map<String, Object>) config.get("interfaces");
                for (Map.Entry<String, Object> entry : interfaces.entrySet()) {
                    String interfaceName = entry.getKey();
                    Map<String, Object> settings = (Map<String, Object>) entry.getValue();
                    
                    ciscoConfig.append("interface ").append(interfaceName).append("\n");
                    
                    if (settings.containsKey("ipAddress") && settings.containsKey("subnetMask")) {
                        String ip = settings.get("ipAddress").toString();
                        String mask = settings.get("subnetMask").toString();
                        ciscoConfig.append("ip address ").append(ip).append(" ").append(mask).append("\n");
                    }
                    
                    if (Boolean.TRUE.equals(settings.get("shutdown"))) {
                        ciscoConfig.append("shutdown\n");
                    } else {
                        ciscoConfig.append("no shutdown\n");
                    }
                    
                    ciscoConfig.append("exit\n");
                }
            }

            if (config.containsKey("routing")) {
                Map<String, Object> routing = (Map<String, Object>) config.get("routing");
                if (routing.containsKey("staticRoutes")) {
                    for (Object routeObj : (Iterable<?>) routing.get("staticRoutes")) {
                        Map<String, String> route = (Map<String, String>) routeObj;
                        ciscoConfig.append("ip route ")
                            .append(route.get("network")).append(" ")
                            .append(route.get("mask")).append(" ")
                            .append(route.get("nextHop")).append("\n");
                    }
                }
            }

            if (config.containsKey("snmp")) {
                Map<String, Object> snmp = (Map<String, Object>) config.get("snmp");
                ciscoConfig.append("snmp-server community ").append(snmp.get("community")).append(" RO\n");
            }

            ciscoConfig.append("end\n");
            ciscoConfig.append("write memory\n");

            String commands = ciscoConfig.toString();
            log.info("Сгенерированные Cisco команды:\n{}", commands);

            ProcessBuilder pb = new ProcessBuilder("ssh", device.getHostname(), commands);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[SSH] {}", line);
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                return new ConfigureResult(
                    true, 
                    "Конфигурация Cisco применена успешно", 
                    Map.of("hostname", device.getHostname(), "commandsSent", ciscoConfig.length())
                );
            } else {
                return new ConfigureResult(
                    false, 
                    "Ошибка применения конфигурации Cisco (exit code: " + exitCode + ")", 
                    Map.of("hostname", device.getHostname(), "output", output.toString())
                );
            }
        } catch (Exception e) {
            log.error("Ошибка при применении конфигурации Cisco", e);
            return new ConfigureResult(false, "Ошибка: " + e.getMessage(), null);
        }
    }

    private ConfigureResult configureMFU(DeviceInfo device, String configJson) {
        try {
            Map<String, Object> config = objectMapper.readValue(configJson, Map.class);
            
            log.info("Применение конфигурации через SNMP для МФУ: {}", device.getHostname());

            StringBuilder snmpCommands = new StringBuilder();
            
            if (config.containsKey("deviceName")) {
                String deviceName = config.get("deviceName").toString();
                snmpCommands.append("snmpset -v 2c -c private ")
                    .append(device.getHostname()).append(" 1.3.6.1.2.1.1.5.0 s \"")
                    .append(deviceName).append("\"\n");
            }

            if (config.containsKey("network")) {
                Map<String, Object> network = (Map<String, Object>) config.get("network");
                if (network.containsKey("ipAddress")) {
                    String ip = network.get("ipAddress").toString();
                    snmpCommands.append("snmpset -v 2c -c private ")
                        .append(device.getHostname()).append(" 1.3.6.1.2.1.4.21.1.1.0 s \"")
                        .append(ip).append("\"\n");
                }
            }

            if (config.containsKey("printSettings")) {
                Map<String, Object> printSettings = (Map<String, Object>) config.get("printSettings");
                snmpCommands.append("snmpset -v 2c -c private ")
                    .append(device.getHostname()).append(" 1.3.6.1.4.1.11.2.3.9.1.1.3.0 i ")
                    .append(printSettings.getOrDefault("duplex", 0)).append("\n");
            }

            String commands = snmpCommands.toString();
            log.info("Сгенерированные SNMP команды:\n{}", commands);

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", commands);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[SNMP] {}", line);
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                return new ConfigureResult(
                    true, 
                    "Конфигурация МФУ применена успешно", 
                    Map.of("hostname", device.getHostname(), "snmpCommandsSent", snmpCommands.length())
                );
            } else {
                return new ConfigureResult(
                    false, 
                    "Ошибка применения конфигурации МФУ (exit code: " + exitCode + ")", 
                    Map.of("hostname", device.getHostname(), "output", output.toString())
                );
            }
        } catch (Exception e) {
            log.error("Ошибка при применении конфигурации МФУ", e);
            return new ConfigureResult(false, "Ошибка: " + e.getMessage(), null);
        }
    }
}
