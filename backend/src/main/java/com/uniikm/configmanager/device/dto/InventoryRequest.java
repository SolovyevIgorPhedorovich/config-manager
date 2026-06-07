package com.uniikm.configmanager.device.dto;

/** Необязательные учётные данные/параметры для инвентаризации одного устройства. */
public record InventoryRequest(
        Integer snmpPort,
        String community,
        String snmpVersion,
        String sshUsername,
        String sshPassword,
        String winrmUsername,
        String winrmPassword
) {}
