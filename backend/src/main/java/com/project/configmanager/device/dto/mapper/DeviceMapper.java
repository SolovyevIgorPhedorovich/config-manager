package com.project.configmanager.device.dto.mapper;

import org.springframework.stereotype.Component;

import com.project.configmanager.device.dto.DeviceRequest;
import com.project.configmanager.device.dto.DeviceResponse;
import com.project.configmanager.device.model.DeviceGroup;
import com.project.configmanager.device.model.DeviceIP;
import com.project.configmanager.device.model.DeviceInfo;
import com.project.configmanager.device.model.DeviceOS;

@Component
public class DeviceMapper {

    public DeviceResponse toResponse(DeviceInfo entity) {
        return new DeviceResponse(
            entity.getId(),
            entity.getHostname(),
            entity.getIps().stream().map(DeviceIP::getIp).toList(),
            entity.getTypeCode(),
            entity.getOsVersionString(),
            entity.getOperatingSystem(),    
            entity.getManufacturer(),       
            entity.getModel(),              
            entity.getGroup() != null ? entity.getGroup().getName() : null,
            entity.getGroup() != null ? entity.getGroup().getDescription() : null,
            entity.getIsActive(),
            entity.getOsVersion() != null ? entity.getOsVersion().getId() : null,
            entity.getGroup() != null ? entity.getGroup().getId() : null
        );
    }

    public DeviceInfo toEntity(DeviceRequest request, DeviceGroup group, DeviceOS os) {
        DeviceInfo device = new DeviceInfo();
        
        // Базовые поля
        device.setHostname(request.hostname());
        device.setTypeCode(request.type());
        device.setIsActive(request.isActive() != null ? request.isActive() : true);
        
        // Новые поля
        if (request.operatingSystem() != null) {
            device.setOperatingSystem(request.operatingSystem());
        }
        
        if (request.manufacturer() != null) {
            device.setManufacturer(request.manufacturer());
        }
        
        if (request.model() != null) {
            device.setModel(request.model());
        }
        
        // Установка группы (если найдена или создана)
        if (group != null) {
            device.setGroup(group);
        } else if (request.groupName() != null && !request.groupName().isBlank()) {
            // Создание новой группы, если не найдена
            DeviceGroup newGroup = new DeviceGroup();
            newGroup.setName(request.groupName());
            if (request.groupDescription() != null) {
                newGroup.setDescription(request.groupDescription());
            }
            device.setGroup(newGroup);
        }
        
        // Установка ОС (если найдена)
        if (os != null) {
            device.setOsVersion(os);
        }
        
        // Обработка IP адресов
        if (request.ips() != null) {
            for (int i = 0; i < request.ips().size(); i++) {
                String ipValue = request.ips().get(i);
                if (ipValue == null || ipValue.isBlank()) {
                    continue;
                }
                
                DeviceIP ip = new DeviceIP();
                ip.setIp(ipValue.trim());
                ip.setIsPrimary(i == 0);
                ip.setDevice(device);
                device.getIps().add(ip);
            }
        }
        
        return device;
    }
    
    // Перегруженный метод для совместимости с существующим кодом
    public DeviceInfo toEntity(DeviceRequest request) {
        return toEntity(request, null, null);
    }
    
    // Метод для обновления существующего устройства
    public void updateEntity(DeviceInfo existing, DeviceRequest request, DeviceGroup group, DeviceOS os) {
        existing.setHostname(request.hostname());
        existing.setTypeCode(request.type());
        existing.setIsActive(request.isActive() != null ? request.isActive() : existing.getIsActive());
        
        if (request.operatingSystem() != null) {
            existing.setOperatingSystem(request.operatingSystem());
        }
        
        if (request.manufacturer() != null) {
            existing.setManufacturer(request.manufacturer());
        }
        
        if (request.model() != null) {
            existing.setModel(request.model());
        }
        
        // Обновление группы
        if (group != null) {
            existing.setGroup(group);
        } else if (request.groupName() != null && !request.groupName().isBlank()) {
            if (existing.getGroup() == null) {
                existing.setGroup(new DeviceGroup());
            }
            existing.getGroup().setName(request.groupName());
            if (request.groupDescription() != null) {
                existing.getGroup().setDescription(request.groupDescription());
            }
        }
        
        // Обновление ОС
        if (os != null) {
            existing.setOsVersion(os);
        }
        
        // Обновление IP адресов (полная замена)
        existing.getIps().clear();
        if (request.ips() != null) {
            for (int i = 0; i < request.ips().size(); i++) {
                String ipValue = request.ips().get(i);
                if (ipValue == null || ipValue.isBlank()) {
                    continue;
                }
                
                DeviceIP ip = new DeviceIP();
                ip.setIp(ipValue.trim());
                ip.setIsPrimary(i == 0);
                ip.setDevice(existing);
                existing.getIps().add(ip);
            }
        }
    }
}