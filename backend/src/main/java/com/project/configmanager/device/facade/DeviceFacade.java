package com.project.configmanager.device.facade;

import com.project.configmanager.device.dto.DeviceRequest;
import com.project.configmanager.device.dto.DeviceResponse;
import com.project.configmanager.device.model.DeviceInfo;

import java.util.List;

public interface DeviceFacade {

    List<DeviceResponse> getAll();

    DeviceInfo getDeviceEntity(Long id);   

    List<DeviceInfo> getDeviceEntities(List<Long> ids);

    DeviceResponse getById(Long id);

    DeviceResponse create(DeviceRequest request);

    DeviceResponse update(Long id, DeviceRequest request);

    void delete(Long id, String actor);

}