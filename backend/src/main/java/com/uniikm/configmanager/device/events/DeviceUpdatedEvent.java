package com.uniikm.configmanager.device.events;

import com.uniikm.configmanager.device.model.DeviceInfo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class DeviceUpdatedEvent {

    private final Long deviceId;
    private final DeviceInfo device;
    private final Object oldState;
    private final Object newState;
}