package com.uniikm.configmanager.config.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class ApplyConfigRequest {
    private List<Long> deviceIds; 
    private String channel; 
    private String target;   
    private Map<String, Object> configData;
    private Map<Long, DeviceCredentials> deviceCredentials;
}