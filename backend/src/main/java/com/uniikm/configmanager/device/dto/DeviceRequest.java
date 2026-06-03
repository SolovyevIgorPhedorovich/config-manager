package com.uniikm.configmanager.device.dto;

import java.util.List;

public record DeviceRequest(
    String hostname,
    List<String> ips,
    Integer type,
    String operatingSystem,
    String manufacturer,  
    String model,           
    Long osVersionId,
    String groupName,
    String groupDescription,
    Boolean isActive,
    String actor
) {}