package com.project.configmanager.controller;

import com.project.configmanager.model.ConfigVersion;
import com.project.configmanager.model.device.DeviceOutput;
import com.project.configmanager.repository.DeviceGroupRepository;
import com.project.configmanager.model.device.DeviceGroup;
import com.project.configmanager.model.device.DeviceIP;
import com.project.configmanager.model.device.DeviceInfo;
import com.project.configmanager.model.device.DeviceInput;
import com.project.configmanager.model.device.DeviceOS;
import com.project.configmanager.service.DeviceService;
import com.project.configmanager.service.NetworkScannerService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;


@RestController
@RequestMapping("/api")
public class DeviceController {

    private final DeviceService deviceService;
    private final ConcurrentHashMap<String, List<DeviceInfo>> scanResultsCache = new ConcurrentHashMap<>();
    
    @Autowired
    NetworkScannerService netScanService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping("/devices")
    public List<DeviceOutput> getAll() {
        return deviceService.getAll().stream()
                .map(this::toOutput)
                .toList();
    }


    @PostMapping("/devices")
    public ResponseEntity<DeviceOutput> add(@RequestBody DeviceInput input) {

        var device = new DeviceInfo();

        if (input.groupName() != null && input.groupName().strip().length() != 0) {
            DeviceGroup deviceGroup = new DeviceGroup();
            deviceGroup = deviceService.add(input);
            device.setGroup(deviceGroup);
        }
        
        device.setHostname(input.hostname());
        if (input.type() != null) {
            device.setTypeCode(input.type());
        }

        if (input.isActive() != null) {
            device.setIsActive(input.isActive());
        }

        var ipsList = input.ips();
        if (ipsList == null || ipsList.isEmpty()) {
            throw new IllegalArgumentException("IP-адрес обязателен");
        }

        for (String ip : ipsList) {
            if (ip == null || ip.trim().isEmpty()) continue;
            var dpi = new DeviceIP();
            dpi.setDevice(device);
            dpi.setIp(ip.trim());
            dpi.setIsPrimary(device.getIps().isEmpty());
            device.getIps().add(dpi);
        }

//        device.setOsVersion(input.osVersionId());

        

        var saved = deviceService.add(device);
        return ResponseEntity.ok(toOutput(saved));
    }



    @GetMapping("/devices/{id}")
    public DeviceOutput getById(@PathVariable Long id) {
        return toOutput(deviceService.getById(id));
    }

    @GetMapping("/devices/scan")
    public ResponseEntity<Map<String, Object>> startScan(
            @RequestParam(name = "ipaddr") String ipaddr,
            @RequestParam(name = "mask", defaultValue = "24") int mask,
            @RequestParam(name = "port", defaultValue = "161") int port,
            @RequestParam(name = "community", defaultValue = "public") String community,
            @RequestParam(name = "snmpv", defaultValue = "v2c") String snmpVersion)      {

        if (mask > 24) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Максимальная маска для сканирования: /24"));
        }

        String taskId = java.util.UUID.randomUUID().toString();

        CompletableFuture<List<DeviceInfo>> scanFuture = netScanService.scanAsync(ipaddr, mask, port, community, snmpVersion)
            .thenApply(devices -> {
                scanResultsCache.put(taskId, devices);
                return devices;
            });

        return ResponseEntity.ok(Map.of(
            "taskId", taskId,
            "status", "started",
            "message", "Сканирование запущено. Для проверки результата /api/devices/scan/status?taskId={id}"
        ));
    }

    // 🟢 Новый endpoint: получение статуса и результатов
    @GetMapping("/devices/scan/status")
    public ResponseEntity<Map<String, Object>> getScanStatus(
            @RequestParam("taskId") String taskId) {

        List<DeviceInfo> devices = scanResultsCache.get(taskId);

        if (devices == null) {
            return ResponseEntity.ok(Map.of(
                "status", "running",
                "message", "Сканирование ещё не завершено"
            ));
        }

        scanResultsCache.remove(taskId);
        return ResponseEntity.ok(Map.of(
            "status", "completed",
            "devices", devices,  
            "count", devices.size()
        ));
    }
    

    @RequestMapping(value = "/devices/{id}/deploy", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<String> deployConfig(
            @PathVariable Long id,
            @RequestBody(required = false) String configJson) {
        return ResponseEntity.ok("Конфигурация для устройства ID " + id + " применена");
    }

    @GetMapping("/devices/{id}/history")
    public List<ConfigVersion> getHistory(@PathVariable Long id) {
        return java.util.Collections.emptyList();    }


    private DeviceOutput toOutput(com.project.configmanager.model.device.DeviceInfo info) {
        List<String> ips = info.getIps() != null ?
            info.getIps().stream()
                 .map(deviceIp -> deviceIp.getIp())
                 .toList() :
            List.of();

        return new DeviceOutput(
            info.getId(),
            info.getHostname(),
            ips,
            // typeCodeName — если у вас есть enum DeviceType:
            java.util.Optional.ofNullable(info.getTypeCode())
                .map(com.project.configmanager.model.enums.DeviceType::fromCode)
                .map(com.project.configmanager.model.enums.DeviceType::getString)
                .orElse("Unknown"),
            info.getOsVersionString(),
            info.getGroupName(),
            info.getIsActive()
        );
    }
}