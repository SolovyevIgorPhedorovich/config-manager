package com.uniikm.configmanager.integration.dto;

public record DeviceProbeResult(
        String ip,
        String hostname,
        String sysDescr,
        String sysLocation,
        String sysContact,
        String vendor,
        String model,
        String detectionMethod   // "SNMP" | "WINRM" | "SSH" | "PORT"
) {}
