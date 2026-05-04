package com.project.configmanager.controller;

import com.project.configmanager.model.ConfigVersion;
import com.project.configmanager.model.Device;
import com.project.configmanager.service.DeviceService;
import com.project.configmanager.service.NetworkScannerService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api")
public class DeviceController {

    private final DeviceService deviceService;
    
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
    public List<Device> getMethodName( @RequestParam("ipaddr") String ipaddr,
        @RequestParam("mask") int mask,
        @RequestParam("port") int port,
        @RequestParam("community") String community,
        @RequestParam("snmpv") String snmpv) throws Exception {
            return netScanService.scan(ipaddr, mask, port, community, snmpv);
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