package com.uniikm.configmanager.device.facade;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uniikm.configmanager.device.dto.BulkDeleteResponse;
import com.uniikm.configmanager.device.dto.DeviceRequest;
import com.uniikm.configmanager.device.dto.DeviceResponse;
import com.uniikm.configmanager.device.model.DeviceInfo;

public interface DeviceFacade {

    List<DeviceResponse> getAll();

    DeviceInfo getDeviceEntity(Long id);

    /** Чтение сущности устройства без исключения: пусто, если не найдено. */
    Optional<DeviceInfo> findDeviceEntity(Long id);

    List<DeviceInfo> getDeviceEntities(List<Long> ids);

    DeviceResponse getById(Long id);

    DeviceResponse create(DeviceRequest request);

    DeviceResponse update(Long id, DeviceRequest request);

    public BulkDeleteResponse bulkDelete(List<Long> ids);

    void delete(Long id);

    /** Фактическая доступность устройств в сети (ping/TCP): id устройства → online. */
    Map<Long, Boolean> getReachability();

}