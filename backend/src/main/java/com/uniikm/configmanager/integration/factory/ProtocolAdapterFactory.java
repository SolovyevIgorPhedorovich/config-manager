package com.uniikm.configmanager.integration.factory;


import org.springframework.stereotype.Component;

import com.uniikm.configmanager.common.dto.ConnectionProtocol;
import com.uniikm.configmanager.common.dto.DeviceCommandTarget;
import com.uniikm.configmanager.integration.adater.ProtocolAdapter;
import com.uniikm.configmanager.integration.adater.SNMPAdapter;
import com.uniikm.configmanager.integration.adater.SSHAdapter;
import com.uniikm.configmanager.integration.adater.WinRMAdapter;
import com.uniikm.configmanager.integration.client.SnmpClient;


@Component
public class ProtocolAdapterFactory {

    private final SnmpClient snmpClient;

    public ProtocolAdapterFactory(SnmpClient snmpClient) {
        this.snmpClient = snmpClient;
    }


    public ProtocolAdapter create(DeviceCommandTarget request) {

        if (request == null) {
            throw new IllegalArgumentException("Request is null");
        }

        ConnectionProtocol protocol = request.protocol();

        return switch (protocol) {

            case SSH -> new SSHAdapter(
                    request.host(),
                    request.port() != null ? request.port() : 22,
                    request.username(),
                    request.password()
            );

            case WINRM -> new WinRMAdapter(
                    request.host(),
                    request.port() != null ? request.port() : 5985,
                    request.username(),
                    request.password()
            );

            case SNMP -> new SNMPAdapter(snmpClient);
        };
    }
}