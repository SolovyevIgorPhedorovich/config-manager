package com.project.configmanager.integration;
import com.project.configmanager.device.model.DeviceInfo;
import com.project.configmanager.model.command.ConnectionProtocol;
import com.project.configmanager.device.enums.DeviceType;
import lombok.RequiredArgsConstructor;

import java.util.Map;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProtocolAdapterFactory {

    public ProtocolAdapter create(DeviceInfo device, Map<String, String> credentials) {

        if (device == null) {
            throw new IllegalArgumentException("Device is null");
        }

        DeviceType type = DeviceType.fromCode(device.getTypeCode());

        return switch (type) {

            case LINUX, CISCO -> createSshAdapter(device);

            case WINDOWS -> createWinrmAdapter(device, credentials);

            default -> throw new UnsupportedOperationException(
                    "Unsupported device type: " + type
            );
        };
    }

    public ProtocolAdapter createAdapter(ConnectionProtocol protocol, Map<String, String> config) {
        
        return switch (protocol) {
            case SSH -> new SSHAdapter(
                config.get("host"),
                Integer.parseInt(config.getOrDefault("port", "22")),
                config.get("username"),
                config.get("password")
            );
            case WINRM -> new WinRMAdapter(
                config.get("host"),
                Integer.parseInt(config.getOrDefault("port", "5985")),
                config.get("username"),
                config.get("password")
            );
            case SNMP -> throw new UnsupportedOperationException("SNMP adapter not yet implemented");
            default -> throw new UnsupportedOperationException("Protocol not supported: " + protocol);
        };
    }

    private ProtocolAdapter createSshAdapter(DeviceInfo device) {

        return new SSHAdapter(
                device.getIps().isEmpty()
                        ? "localhost"
                        : device.getIps().get(0).getIp(),
                22,
                null,
                null
        );
    }


    private ProtocolAdapter createWinrmAdapter(DeviceInfo device, Map<String, String> credentials) {

        String host = device.getIps().isEmpty()
                ? "localhost"
                : device.getIps().get(0).getIp();

        String username = credentials.get("user");
        String password = credentials.get("password");

        return new WinRMAdapter(
                host,
                5985,
                username,
                password
        );
    }
}