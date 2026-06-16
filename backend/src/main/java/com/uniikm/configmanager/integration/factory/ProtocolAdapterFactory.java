package com.uniikm.configmanager.integration.factory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.uniikm.configmanager.common.dto.ConnectionProtocol;
import com.uniikm.configmanager.common.dto.DeviceCommandTarget;
import com.uniikm.configmanager.integration.adapter.ProtocolAdapter;
import com.uniikm.configmanager.integration.adapter.SNMPAdapter;
import com.uniikm.configmanager.integration.adapter.SSHAdapter;
import com.uniikm.configmanager.integration.adapter.WinRMAdapter;
import com.uniikm.configmanager.integration.client.SnmpClient;

@Component
public class ProtocolAdapterFactory {

    private final SnmpClient snmpClient;

    @Value("${ssh.default-port:22}")
    private int sshDefaultPort;

    @Value("${ssh.connect-timeout-ms:30000}")
    private int sshConnectTimeoutMs;

    @Value("${winrm.default-port:5985}")
    private int winrmDefaultPort;

    @Value("${winrm.command-timeout-ms:30000}")
    private long winrmCommandTimeoutMs;

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
                    request.port() != null ? request.port() : sshDefaultPort,
                    request.username(),
                    request.password(),
                    sshConnectTimeoutMs
            );
            case WINRM -> new WinRMAdapter(
                    request.host(),
                    request.port() != null ? request.port() : winrmDefaultPort,
                    request.username(),
                    request.password(),
                    null,
                    winrmCommandTimeoutMs
            );
            case SNMP -> new SNMPAdapter(snmpClient);
        };
    }
}
