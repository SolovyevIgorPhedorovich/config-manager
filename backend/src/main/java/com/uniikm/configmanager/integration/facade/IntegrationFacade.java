package com.uniikm.configmanager.integration.facade;

import com.uniikm.configmanager.config.dto.DeviceCredentials;
import com.uniikm.configmanager.device.model.DeviceInfo;
import com.uniikm.configmanager.integration.dto.CommandGroupStatus;

public interface IntegrationFacade {

    CommandGroupStatus executeConfiguration(
            DeviceInfo device,
            DeviceCredentials credentials,
            String command
    );
}