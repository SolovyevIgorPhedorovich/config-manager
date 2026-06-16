package com.uniikm.configmanager.integration.facade;

import org.springframework.stereotype.Service;

import com.uniikm.configmanager.integration.dto.CommandExecutionRequest;
import com.uniikm.configmanager.integration.dto.CommandGroupStatus;
import com.uniikm.configmanager.integration.dto.DeviceProbeResult;
import com.uniikm.configmanager.integration.dto.ScanConfig;
import com.uniikm.configmanager.integration.server.RemoteCommandService;
import com.uniikm.configmanager.integration.service.NetworkProbeService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IntegrationFacadeImpl implements IntegrationFacade {

    private final NetworkProbeService networkProbeService;
    private final RemoteCommandService remoteCommandService;

    @Override
    public int getPingTimeoutMs() {
        return networkProbeService.getPingTimeoutMs();
    }

    @Override
    public boolean isReachable(String ip) {
        return networkProbeService.isReachable(ip);
    }

    @Override
    public DeviceProbeResult probe(String ip, ScanConfig config) {
        return networkProbeService.probe(ip, config);
    }

    @Override
    public CommandGroupStatus executeAsync(CommandExecutionRequest request) {
        return remoteCommandService.executeAsync(request);
    }
}
