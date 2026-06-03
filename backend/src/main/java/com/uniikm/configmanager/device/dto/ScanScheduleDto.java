package com.uniikm.configmanager.device.dto;

public record ScanScheduleDto(
        String name,
        String subnet,
        int mask,
        int port,
        String community,
        String snmpVersion,
        String scanMode,
        String cronExpression,
        boolean enabled,
        String sshUsername,
        String sshPassword,
        String winrmUsername,
        String winrmPassword
) {}
