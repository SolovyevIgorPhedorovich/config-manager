package com.project.configmanager.device.facade;

import java.util.List;

import org.springframework.stereotype.Service;

import com.project.configmanager.device.dto.DeviceRequest;
import com.project.configmanager.device.dto.DeviceResponse;
import com.project.configmanager.device.dto.mapper.DeviceMapper;
import com.project.configmanager.device.model.DeviceInfo;
import com.project.configmanager.device.service.DeviceService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeviceFacadeImpl implements DeviceFacade {

    private final DeviceService deviceService;
    private final DeviceMapper deviceMapper;
    @Override
    public List<DeviceResponse> getAll() {
        return deviceService.getAll()
                .stream()
                .map(deviceMapper::toResponse)
                .toList();
    }

    @Override
    public DeviceResponse getById(Long id) {
        return deviceMapper.toResponse(
                deviceService.getById(id)
        );
    }

    @Override
    public DeviceResponse create(DeviceRequest request) {
        return deviceMapper.toResponse(
                deviceService.create(deviceMapper.toEntity(request), request.actor())
        );
    }

    @Override
    public DeviceResponse update(Long id, DeviceRequest request) {
        return deviceMapper.toResponse(
                deviceService.update(id, deviceMapper.toEntity(request), request.actor())
        );
    }

    @Override
    public void delete(Long id, String actor) {
        deviceService.delete(id, actor);
    }

    @Override
    public DeviceInfo getDeviceEntity(Long id) {
       return deviceService.getById(id);
    }

    @Override
    public List<DeviceInfo> getDeviceEntities(List<Long> ids) {
        return deviceService.getAllByIds(ids);
    }
}