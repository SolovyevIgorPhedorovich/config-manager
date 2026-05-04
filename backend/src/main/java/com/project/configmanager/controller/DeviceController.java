package com.project.configmanager.controller;

import com.project.configmanager.model.ConfigVersion;
import com.project.configmanager.model.Device;
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
    private final ConcurrentHashMap<String, List<Device>> scanResultsCache = new ConcurrentHashMap<>();
    
    @Autowired
    NetworkScannerService netScanService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping("/devices")
    public List<Device> getAll() {
        return deviceService.getAll();
    }

    @PostMapping("/devices")
    public Device add(@RequestBody Device device) {
        return deviceService.add(device);
    }

    @GetMapping("/devices/{id}")
    public Device getById(@PathVariable Long id) {
        return deviceService.getById(id);
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

        CompletableFuture<List<Device>> scanFuture = netScanService.scanAsync(ipaddr, mask, port, community, snmpVersion)
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

        List<Device> devices = scanResultsCache.get(taskId);

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
    

    @PostMapping("/devices/{id}/deploy")
    public ResponseEntity<String> deployConfig(
            @PathVariable Long id,
            @RequestBody(required = false) String configJson) {
        return ResponseEntity.ok("Конфигурация для устройства ID " + id + " применена");
    }

    @GetMapping("/devices/{id}/history")
    public List<ConfigVersion> getHistory(@PathVariable Long id) {
        return java.util.Collections.emptyList();    }
}