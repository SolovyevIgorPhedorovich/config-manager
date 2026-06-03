package com.uniikm.configmanager.device.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uniikm.configmanager.device.service.NetworkScannerService;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceScanController {

    @Autowired
    private NetworkScannerService scannerService;

    @GetMapping("/scan")
    public ResponseEntity<Map<String, Object>> startScan(
            @RequestParam String ipaddr,
            @RequestParam(defaultValue = "24") int mask,
            @RequestParam(defaultValue = "161") int port,
            @RequestParam(defaultValue = "public") String community,
            @RequestParam(defaultValue = "v2c") String snmpv) {

        return scannerService.startScan(ipaddr, mask, port, community, snmpv);
    }

    @GetMapping("/scan/status")
    public ResponseEntity<Object> getScanStatus(@RequestParam String taskId) {
        return scannerService.getResult(taskId);
    }
}