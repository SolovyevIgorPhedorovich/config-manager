package com.uniikm.configmanager.device.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uniikm.configmanager.device.dto.InventoryRequest;
import com.uniikm.configmanager.device.service.NetworkScannerService;
import com.uniikm.configmanager.device.service.ScanCredentialService;
import com.uniikm.configmanager.device.service.ScanCredentialService.ResolvedCreds;
import com.uniikm.configmanager.integration.dto.ScanConfig;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceScanController {

    @Autowired
    private NetworkScannerService scannerService;

    @Autowired
    private ScanCredentialService credentialService;

    @GetMapping("/scan")
    public ResponseEntity<Map<String, Object>> startScan(
            @RequestParam String ipaddr,
            @RequestParam(defaultValue = "24") int mask,
            @RequestParam(defaultValue = "161") int port,
            @RequestParam(defaultValue = "public") String community,
            @RequestParam(defaultValue = "v2c") String snmpv,
            @RequestParam(defaultValue = "all") String scanMode,
            @RequestParam(required = false) Long credentialId,
            // SNMPv3 (USM) — логин и пароли аутентификации/шифрования
            @RequestParam(required = false) String securityName,
            @RequestParam(required = false) String authProtocol,
            @RequestParam(required = false) String authPassword,
            @RequestParam(required = false) String privProtocol,
            @RequestParam(required = false) String privPassword,
            // SSH / WinRM
            @RequestParam(required = false) String sshUsername,
            @RequestParam(required = false) String sshPassword,
            @RequestParam(required = false) String winrmUsername,
            @RequestParam(required = false) String winrmPassword) {

        // Если указан профиль доступа — берём креды из него (расшифровываем на сервере);
        // иначе используем переданные разово.
        if (credentialId != null) {
            ResolvedCreds creds = credentialService.resolve(credentialId);
            sshUsername   = creds.sshUsername();
            sshPassword   = creds.sshPassword();
            winrmUsername = creds.winrmUsername();
            winrmPassword = creds.winrmPassword();
        }

        return scannerService.startScan(ipaddr, mask, port, community, snmpv, scanMode,
                securityName, authProtocol, authPassword, privProtocol, privPassword,
                sshUsername, sshPassword, winrmUsername, winrmPassword);
    }

    @GetMapping("/scan/status")
    public ResponseEntity<Object> getScanStatus(@RequestParam String taskId) {
        return scannerService.getResult(taskId);
    }

    /** Инвентаризация одного устройства: опросить по сети и обновить его данные/ОС. */
    @PostMapping("/{id}/inventory")
    public ResponseEntity<Map<String, Object>> inventory(
            @PathVariable Long id,
            @RequestBody(required = false) InventoryRequest req) {

        ScanConfig config = new ScanConfig(
                req != null && req.snmpPort() != null ? req.snmpPort() : 161,
                req != null && req.community() != null ? req.community() : "public",
                req != null && req.snmpVersion() != null ? req.snmpVersion() : "v2c",
                null, null, null, null, null,   // SNMPv3 не используется при инвентаризации
                req != null ? req.sshUsername() : null,
                req != null ? req.sshPassword() : null,
                req != null ? req.winrmUsername() : null,
                req != null ? req.winrmPassword() : null,
                true
        );
        return ResponseEntity.ok(scannerService.inventoryDevice(id, config));
    }
}
