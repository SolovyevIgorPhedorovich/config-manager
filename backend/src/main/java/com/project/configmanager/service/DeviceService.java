package com.project.configmanager.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.configmanager.model.AuditLog;
import com.project.configmanager.model.ConfigVersion;
import com.project.configmanager.model.device.DeviceGroup;
import com.project.configmanager.model.device.DeviceInfo;
import com.project.configmanager.model.device.DeviceInput;
import com.project.configmanager.model.enums.AuditAction;
import com.project.configmanager.repository.AuditLogRepository;
import com.project.configmanager.repository.ConfigVersionRepository;
import com.project.configmanager.repository.DeviceGroupRepository;
import com.project.configmanager.repository.DeviceRepository;

@Service
public class DeviceService {

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepo = deviceRepository;
    }

    @Autowired
    private DeviceRepository deviceRepo;

    @Autowired
    private DeviceGroupRepository groupRepo;
    
    @Autowired
    private ConfigVersionRepository configRepo;
    
    @Autowired
    private AuditLogRepository auditLogRepository;
    
    @Autowired
    private ObjectMapper mapper;

    public void applyConfigToDevice(Long deviceId, String newConfig) {
        DeviceInfo device = deviceRepo.findById(deviceId)
            .orElseThrow(() -> new RuntimeException("Device not found"));

        // Сохраняем текущую конфигурацию в историю
        ConfigVersion oldVersion = configRepo.findFirstByDeviceOrderByAppliedAtDesc(device);
        String previousConfig = oldVersion != null ? oldVersion.getNewConfig() : "";

        ConfigVersion newVer = ConfigVersion.builder()
            .device(device)
            .configTypeCode(0)
            .versionNumber(oldVersion != null ? oldVersion.getVersionNumber() + 1 : 1)
            .oldConfig(mapper.convertValue(previousConfig, String.class)) // если JSONB
            .newConfig(newConfig)
            .rollbackAvailable(true)
            .build();

        configRepo.save(newVer);

        // Логируем
        AuditLog log = AuditLog.builder()
            .userId("admin@example.com")
            .actionType(AuditAction.CONFIG_APPLIED)
            .targetDevice(device)
            .oldConfig("version " + (newVer.getVersionNumber() - 1))
            .newConfig("version " + newVer.getVersionNumber())
            .statusValue(1)
            .build();

        auditLogRepository.save(log);
    }

     public List<DeviceInfo> getAll() {
        return deviceRepo.findAll();
    }

    public DeviceInfo getById(Long id) {
        Optional<DeviceInfo> optionalDevice = deviceRepo.findById(id);
        return optionalDevice.orElseThrow(() -> 
            new RuntimeException("Устройство с ID " + id + " не найдено"));
    }

    public DeviceGroup add(DeviceInput input) {
        return groupRepo.findByName(input.groupName())
            .orElseGet(() -> {
                var newGroup = new DeviceGroup();
                newGroup.setName(input.groupName());
                return groupRepo.save(newGroup); 
            });
    }

    public DeviceInfo add(DeviceInfo device) {
        if (device.getHostname() == null || device.getHostname().isEmpty()) {
            throw new IllegalArgumentException("Имя хоста (hostname) обязательно");
        }
        if (device.getIps() == null || !isValidIp(device.getIps().get(0).getIp())) {
            throw new IllegalArgumentException("Некорректный IP-адрес: " + device.getIps().get(0).getIp());
        }

        DeviceInfo saved = deviceRepo.save(device);
        
        AuditLog log = AuditLog.builder()
            .userId("system")
            .actionType(AuditAction.DEVICE_ADDED)
            .targetDevice(saved)
            .oldConfig(null)
            .newConfig(mapper.convertValue(saved, String.class))
            .statusValue(1)
            .build();
        
        auditLogRepository.save(log);
        
        return saved;
    }

    public List<DeviceInfo> addAll(List<DeviceInfo> devicesList) {
        return deviceRepo.saveAll(devicesList);
    }

    public DeviceInfo update(Long id, DeviceInfo updatedDevice) {
       DeviceInfo existing = deviceRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Устройство не найдено"));

        String oldConfig = mapper.convertValue(existing, String.class);

        existing.setHostname(updatedDevice.getHostname());
        existing.setTypeCode(updatedDevice.getTypeCode());
        existing.setGroup(updatedDevice.getGroup());
        existing.setOsVersion(updatedDevice.getOsVersion());
        existing.setIsActive(updatedDevice.getIsActive());

        if (updatedDevice.getIps() != null && !updatedDevice.getIps().isEmpty()) {
            existing.getIps().clear();
            for (int i = 0; i < updatedDevice.getIps().size(); i++) {
                var newIp = updatedDevice.getIps().get(i);
                if (newIp == null || newIp.getIp() == null || newIp.getIp().trim().isEmpty()) {
                    continue;
                }
                var ip = new com.project.configmanager.model.device.DeviceIP();
                ip.setDevice(existing);
                ip.setIp(newIp.getIp().trim());
                ip.setIfName(newIp.getIfName());
                ip.setIsPrimary(i == 0);
                existing.getIps().add(ip);
            }
        }
        
        DeviceInfo saved = deviceRepo.save(existing);
        
        AuditLog log = AuditLog.builder()
            .userId("system")
            .actionType(AuditAction.DEVICE_UPDATED)
            .targetDevice(saved)
            .oldConfig(oldConfig)
            .newConfig(mapper.convertValue(saved, String.class))
            .statusValue(1)
            .build();
        
        auditLogRepository.save(log);
        
        return saved;
    }

    public void delete(Long id) {
        DeviceInfo device = deviceRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Устройство не найдено для удаления"));
        
        String oldConfig = mapper.convertValue(device, String.class);
        
        if (deviceRepo.existsById(id)) {
            deviceRepo.deleteById(id);
        } else {
            throw new RuntimeException("Устройство не найдено для удаления");
        }
        
        AuditLog log = AuditLog.builder()
            .userId("system")
            .actionType(AuditAction.DEVICE_DELETED)
            .targetDevice(device)
            .oldConfig(oldConfig)
            .newConfig(null)
            .statusValue(1)
            .build();
        
        auditLogRepository.save(log);
    }

    private boolean isValidIp(String ip) {
        return ip != null && ip.matches("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|25[0-4]|[01]?[0-9][0-9]?)$");
    }

}