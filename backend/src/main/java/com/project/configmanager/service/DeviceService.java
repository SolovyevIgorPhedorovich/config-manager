package com.project.configmanager.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.configmanager.model.AuditLog;
import com.project.configmanager.model.ConfigVersion;
import com.project.configmanager.model.Device;
import com.project.configmanager.model.enums.AuditAction;
import com.project.configmanager.model.enums.ConfigType;
import com.project.configmanager.model.enums.TaskStatus;
import com.project.configmanager.repository.AuditLogRepository;
import com.project.configmanager.repository.ConfigVersionRepository;
import com.project.configmanager.repository.DeviceRepository;

import jakarta.transaction.Transactional;

@Service
public class DeviceService {

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepo = deviceRepository;
    }

    @Autowired
    private DeviceRepository deviceRepo;
    
    @Autowired
    private ConfigVersionRepository configRepo;
    
    @Autowired
    private AuditLogRepository auditLogRepository;
    
    @Autowired
    private ObjectMapper mapper;

    public void applyConfigToDevice(Long deviceId, String newConfig) {
        Device device = deviceRepo.findById(deviceId)
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

     public List<Device> getAll() {
        return deviceRepo.findAll();
    }

    public Device getById(Long id) {
        Optional<Device> optionalDevice = deviceRepo.findById(id);
        return optionalDevice.orElseThrow(() -> 
            new RuntimeException("Устройство с ID " + id + " не найдено"));
    }

    public Device add(Device device) {
        if (device.getHostname() == null || device.getHostname().isEmpty()) {
            throw new IllegalArgumentException("Имя хоста (hostname) обязательно");
        }
        if (device.getIp() == null || !isValidIp(device.getIp())) {
            throw new IllegalArgumentException("Некорректный IP-адрес: " + device.getIp());
        }

        return deviceRepo.save(device);
    }

    public List<Device> addAll(List<Device> devicesList) {
        return deviceRepo.saveAll(devicesList);
    }

    public Device update(Long id, Device updatedDevice) {
        if (!deviceRepo.existsById(id)) {
            throw new RuntimeException("Устройство не найдено");
        }
        updatedDevice.setId(id);
        return deviceRepo.save(updatedDevice);
    }

    public void delete(Long id) {
        if (deviceRepo.existsById(id)) {
            deviceRepo.deleteById(id);
        } else {
            throw new RuntimeException("Устройство не найдено для удаления");
        }
    }

    private boolean isValidIp(String ip) {
        return ip != null && ip.matches("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|25[0-4]|[01]?[0-9][0-9]?)$");
    }
}