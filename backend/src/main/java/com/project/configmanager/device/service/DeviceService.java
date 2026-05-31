package com.project.configmanager.device.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.configmanager.device.events.DeviceEvent;
import com.project.configmanager.device.model.DeviceGroup;
import com.project.configmanager.device.model.DeviceInfo;
import com.project.configmanager.device.model.DeviceOS;
import com.project.configmanager.device.model.DeviceIP;
import com.project.configmanager.device.repository.DeviceGroupRepository;
import com.project.configmanager.device.repository.DeviceOSRepository;
import com.project.configmanager.device.repository.DeviceRepository;
import com.project.configmanager.audit.enums.AuditAction;

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


    @Transactional
    public DeviceInfo create(DeviceInfo device, String actor) {

        validateDevice(device);

        DeviceGroup group = resolveOrCreateGroup(device.getGroupName() != null ? device.getGroupName() : null);
        DeviceOS os = resolveOrCreateOS(device.getOsVersion().getName() != null ? device.getOsVersion().getName() : null);
        device.setGroup(group);
        device.setOsVersion(os);
        DeviceInfo saved = deviceRepository.save(device);

        publishEvent(AuditAction.DEVICE_ADDED,
                saved,
                null,
                saved,
                actor);

        return saved;
    }
    

    @Transactional
    public DeviceInfo update(Long id, DeviceInfo updated, String actor) {

        DeviceInfo existing = deviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Device not found"));

        String beforeState = toJson(existing);

        applyUpdates(existing, updated);

        DeviceGroup group = resolveOrCreateGroup(updated.getGroup() != null ? updated.getGroup().getName() : null);
        DeviceOS os = resolveOrCreateOS(updated.getOsVersion().getName() != null ? updated.getOsVersion().getName() : null);
        existing.setGroup(group);
        existing.setOsVersion(os);
        DeviceInfo saved = deviceRepository.save(existing);

        publishEvent(
                AuditAction.DEVICE_UPDATED,
                saved,
                actor,
                beforeState,
                toJson(saved)
        );

        return saved;
    }

    @Transactional
    public void delete(Long id, String actor) {

        DeviceInfo device = deviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Device not found"));

        String oldState = toJson(device);

        deviceRepository.delete(device);

        publishEvent(AuditAction.DEVICE_DELETED,
                device,
                oldState,
                null,
                actor);
    }

    public List<DeviceInfo> getAll() {
        return deviceRepository.findAll();
    }

    public DeviceInfo getById(Long id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Устройиство не найдено"));
    }

    @Transactional
    public void applyConfig(Long deviceId, String newConfig, String actor) {

        DeviceInfo device = getById(deviceId);

        publishEvent(AuditAction.CONFIG_APPLIED,
                device,
                null,
                newConfig,
                actor);
    }

    @Transactional
    public DeviceGroup resolveOrCreateGroup(String groupName) {

        if (groupName == null || groupName.isBlank()) {
            return null;
        }

        return groupRepository.findByName(groupName)
                .orElseGet(() -> groupRepository.save(
                        DeviceGroup.builder()
                                .name(groupName)
                                .build()
                ));
    }

    @Transactional
    public DeviceOS resolveOrCreateOS(String osName) {
        if (osName == null || osName.isBlank()) {
            return null;
        }

        return osRepository.findByName(osName)
            .orElseGet(() -> osRepository.save(DeviceOS.builder().name(osName).build()));
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

    private void applyUpdates(DeviceInfo existing, DeviceInfo updated) {

        existing.setHostname(updated.getHostname());
        existing.setTypeCode(updated.getTypeCode());
        existing.setGroup(updated.getGroup());
        existing.setOsVersion(updated.getOsVersion());
        existing.setIsActive(updated.getIsActive());

        if (updated.getIps() != null && !updated.getIps().isEmpty()) {

            existing.getIps().clear();

            for (int i = 0; i < updated.getIps().size(); i++) {

                DeviceIP ip = updated.getIps().get(i);

                if (ip.getIp() == null || ip.getIp().isBlank()) {
                    continue;
                }

                ip.setDevice(existing);
                ip.setIsPrimary(i == 0);

                existing.getIps().add(ip);
            }
        }
    }

    private void publishEvent(
            AuditAction action,
            DeviceInfo device,
            String actor,
            Object beforeState,
            Object afterState
    ) {
        eventPublisher.publishEvent(
                new DeviceEvent(
                        action,
                        device,
                        actor,
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