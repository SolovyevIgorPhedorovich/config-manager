package com.uniikm.configmanager.device.facade;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.uniikm.configmanager.device.dto.BulkDeleteResponse;
import com.uniikm.configmanager.device.dto.DeleteError;
import com.uniikm.configmanager.device.dto.DeviceRequest;
import com.uniikm.configmanager.device.dto.DeviceResponse;
import com.uniikm.configmanager.device.dto.mapper.DeviceMapper;
import com.uniikm.configmanager.device.model.DeviceInfo;
import com.uniikm.configmanager.device.service.DeviceService;

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
                deviceService.create(request, request.actor())
        );
    }

    @Override
    public DeviceResponse update(Long id, DeviceRequest request) {
        return deviceMapper.toResponse(
                deviceService.update(id, request, request.actor())
        );
    }

    @Override
    public void delete(Long id) {
        deviceService.delete(id);
    }
    
    @Override
    public BulkDeleteResponse bulkDelete(List<Long> ids) {
        int successCount = 0;
        int failCount = 0;
        List<DeleteError> errors = new ArrayList<>();
        
        for (Long id : ids) {
            try {
                deviceService.delete(id);
                successCount++;
            } catch (Exception e) {
                failCount++;
                errors.add(deviceMapper.toDeleteError(id, e.getMessage()));
            }
        }
        
        return deviceMapper.toBulkDeleteResponse(successCount, failCount, errors);
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