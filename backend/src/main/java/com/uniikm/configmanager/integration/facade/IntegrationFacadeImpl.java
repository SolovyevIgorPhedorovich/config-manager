package com.uniikm.configmanager.integration.facade;

import java.util.List;

import org.springframework.stereotype.Service;

import com.uniikm.configmanager.common.dto.ConnectionProtocol;
import com.uniikm.configmanager.common.dto.DeviceCommandTarget;
import com.uniikm.configmanager.config.dto.DeviceCredentials;
import com.uniikm.configmanager.device.enums.DeviceType;
import com.uniikm.configmanager.device.model.DeviceInfo;
import com.uniikm.configmanager.integration.dto.CommandExecutionRequest;
import com.uniikm.configmanager.integration.dto.CommandGroupStatus;
import com.uniikm.configmanager.integration.server.RemoteCommandService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IntegrationFacadeImpl implements IntegrationFacade {

    private final RemoteCommandService remoteCommandService;

    @Override
    public CommandGroupStatus executeConfiguration(
            DeviceInfo device,
            DeviceCredentials credentials,
            String command
    ) {

        DeviceCommandTarget target = buildTarget(device, credentials);

        CommandExecutionRequest request =
                new CommandExecutionRequest(
                        command,
                        List.of(target),
                        null,
                        null
                );

        return remoteCommandService.executeAsync(request);
    }

    private DeviceCommandTarget buildTarget(
            DeviceInfo device,
            DeviceCredentials credentials
    ) {

        String ip = device.getIps().isEmpty()
                ? device.getHostname()
                : device.getIps().get(0).getIp();

        ConnectionProtocol protocol =
                device.getType() == DeviceType.WINDOWS
                        ? ConnectionProtocol.WINRM
                        : ConnectionProtocol.SSH;

        return new DeviceCommandTarget(
                ip,
                credentials.getPort(),
                credentials.getUsername(),
                credentials.getPassword(),
                protocol,
                null,
                null,
                null
        );
    }
}
