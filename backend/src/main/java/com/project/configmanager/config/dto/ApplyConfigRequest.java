package com.project.configmanager.config.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class ApplyConfigRequest {
    private List<Long> deviceIds;   // список устройств
    private String channel;          // "ssh", "winrm"
    private String target;           // "linux", "windows"
    private Map<String, Object> configData;       // полная конфигурация (JSON из формы)
    private Map<Long, DeviceCredentials> deviceCredentials;
}