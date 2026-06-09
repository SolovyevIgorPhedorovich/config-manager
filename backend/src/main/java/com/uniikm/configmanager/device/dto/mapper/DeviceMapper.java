package com.uniikm.configmanager.device.dto.mapper;

import org.springframework.stereotype.Component;

import com.uniikm.configmanager.device.dto.BulkDeleteResponse;
import com.uniikm.configmanager.device.dto.DeleteError;
import com.uniikm.configmanager.device.dto.DeviceRequest;
import com.uniikm.configmanager.device.dto.DeviceResponse;
import com.uniikm.configmanager.device.model.DeviceGroup;
import com.uniikm.configmanager.device.model.DeviceIP;
import com.uniikm.configmanager.device.model.DeviceInfo;
import com.uniikm.configmanager.device.model.DeviceOS;

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
            entity.getOsVersion() != null ? entity.getOsVersion().getVendor() : "",
            entity.getOsVersion() != null ? entity.getOsVersion().getModel() : "",              
            entity.getGroup() != null ? entity.getGroup().getName() : "",
            entity.getGroup() != null ? entity.getGroup().getDescription() != null ? entity.getGroup().getDescription() : "" : "",
            entity.getIsActive(),
            entity.getOsVersion() != null ? entity.getOsVersion().getId() : null,
            entity.getConfigDrift(),
            entity.getDriftVersionId()
        );
    }

     public DeviceInfo toEntity(DeviceRequest request, DeviceGroup group, DeviceOS os) {
        DeviceInfo device = new DeviceInfo();

        device.setHostname(request.hostname());
        device.setTypeCode(request.type());
        device.setIsActive(request.isActive() != null ? request.isActive() : true);
        
        if (group != null) {
            device.setGroup(group);
        }
        
        if (os != null) {
            device.setOsVersion(os);
        }
        
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
    
    public DeviceGroup toDeviceGroup(DeviceRequest request) {
        if (request.groupName() == null || request.groupName().isBlank()) {
            return null;
        }
        
        return DeviceGroup.builder()
                .name(request.groupName())
                .description(request.groupDescription())
                .build();
    }
    
    public DeviceOS toDeviceOS(DeviceRequest request) {
        if (request.operatingSystem() == null && request.manufacturer() == null && request.model() == null) {
            return null;
        }
        
        return DeviceOS.builder()
                .name(request.operatingSystem() != null ? request.operatingSystem() : "Unknown")
                .vendor(request.manufacturer())
                .model(request.model())
                .build();
    }
    
    public DeviceInfo toEntity(DeviceRequest request) {
        return toEntity(request, null, null);
    }
    
    public void updateEntity(DeviceInfo existing, DeviceRequest request, DeviceGroup group, DeviceOS os) {
        existing.setHostname(request.hostname());
        existing.setTypeCode(request.type());
        existing.setIsActive(request.isActive() != null ? request.isActive() : existing.getIsActive());
        
        // Обновление группы
        if (group != null) {
            existing.setGroup(group);
        }
        
        // Обновление ОС
        if (os != null) {
            existing.setOsVersion(os);
        }
        
        // Обновление IP адресов
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
    
    // Метод для создания BulkDeleteResponse
    public BulkDeleteResponse toBulkDeleteResponse(int successCount, int failCount, java.util.List<DeleteError> errors) {
        return new BulkDeleteResponse(successCount, failCount, errors);
    }
    
    // Метод для создания DeleteError
    public DeleteError toDeleteError(Long id, String error) {
        return new DeleteError(id, error);
    }
}