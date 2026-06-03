package com.uniikm.configmanager.device.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniikm.configmanager.audit.enums.AuditAction;
import com.uniikm.configmanager.device.dto.DeviceRequest;
import com.uniikm.configmanager.device.dto.mapper.DeviceMapper;
import com.uniikm.configmanager.device.events.DeviceEvent;
import com.uniikm.configmanager.device.model.DeviceGroup;
import com.uniikm.configmanager.device.model.DeviceIP;
import com.uniikm.configmanager.device.model.DeviceInfo;
import com.uniikm.configmanager.device.model.DeviceOS;
import com.uniikm.configmanager.device.repository.DeviceGroupRepository;
import com.uniikm.configmanager.device.repository.DeviceOSRepository;
import com.uniikm.configmanager.device.repository.DeviceRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final DeviceGroupRepository groupRepository;
    private final DeviceOSRepository osRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final DeviceMapper deviceMapper;

    @Transactional
    public DeviceInfo create(DeviceRequest request, String actor) {
        DeviceGroup group = resolveOrCreateGroup(request);
        
        DeviceOS os = resolveOrCreateOS(request);
        
        DeviceInfo device = deviceMapper.toEntity(request, group, os);
        validateDevice(device);
        
        DeviceInfo saved = deviceRepository.save(device);

        publishEvent(AuditAction.DEVICE_ADDED,
                saved,
                null,
                saved);

        return saved;
    }
    
    @Transactional
    public DeviceInfo update(Long id, DeviceRequest request, String actor) {

        DeviceInfo existing = deviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Device not found"));

        String beforeState = toJson(existing);

        DeviceGroup group = resolveOrCreateGroup(request);
        
        DeviceOS os = resolveOrCreateOS(request);
        
        deviceMapper.updateEntity(existing, request, group, os);
        
        DeviceInfo saved = deviceRepository.save(existing);

        publishEvent(
                AuditAction.DEVICE_UPDATED,
                saved,
                beforeState,
                toJson(saved)
        );

        return saved;
    }

    @Transactional
    public void delete(Long id) {

        DeviceInfo device = deviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Device not found"));

        String oldState = toJson(device);

        deviceRepository.delete(device);

        publishEvent(AuditAction.DEVICE_DELETED,
                device,
                oldState,
                null);
    }

    public List<DeviceInfo> getAll() {
        return deviceRepository.findAll();
    }

    public DeviceInfo getById(Long id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Устройство не найдено"));
    }

    @Transactional
    public void applyConfig(Long deviceId, String newConfig) {

        DeviceInfo device = getById(deviceId);

        publishEvent(AuditAction.CONFIG_APPLIED,
                device,
                null,
                newConfig);
    }

    @Transactional
    public DeviceGroup resolveOrCreateGroup(DeviceRequest request) {
        String groupName = request.groupName();
        
        if (groupName == null || groupName.isBlank()) {
            return null;
        }

        return groupRepository.findByName(groupName)
                .orElseGet(() -> {
                    DeviceGroup newGroup = deviceMapper.toDeviceGroup(request);
                    return groupRepository.save(newGroup);
                });
    }

    @Transactional
    public DeviceOS resolveOrCreateOS(DeviceRequest request) {
        String osName = request.operatingSystem();
        String vendor = request.manufacturer();
        String model = request.model();
        
        if (osName == null || osName.isBlank()) {
            return null;
        }

        // Ищем по name, vendor, model
        return osRepository.findByNameAndVendorAndModel(osName, vendor, model)
                .orElseGet(() -> {
                    DeviceOS newOs = deviceMapper.toDeviceOS(request);
                    return osRepository.save(newOs);
                });
    }

    private void validateDevice(DeviceInfo device) {
        if (device.getHostname() == null || device.getHostname().isBlank()) {
            throw new IllegalArgumentException("Hostname is required");
        }

        if (device.getIps() == null || device.getIps().isEmpty()) {
            throw new IllegalArgumentException("At least one IP is required");
        }

        device.getIps().forEach(ip -> ip.setDevice(device));
    }

    private void publishEvent(
            AuditAction action,
            DeviceInfo device,
            Object beforeState,
            Object afterState
    ) {
        eventPublisher.publishEvent(
                new DeviceEvent(
                        action,
                        device,
                        beforeState,
                        afterState
                )
        );
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    public List<DeviceInfo> getAllByIds(List<Long> ids) {
        return deviceRepository.findAllById(ids);
    }
}