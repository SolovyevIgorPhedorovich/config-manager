package com.project.configmanager.device.dto;

import java.util.List;

public record DeviceResponse(
    Long id,
    String hostname,
    List<String> ips,
    Integer typeCode,
    String osVersion,          
    String operatingSystem,    
    String manufacturer,       
    String model,              
    String groupName,          
    String groupDescription,   
    Boolean isActive,
    Long osVersionId,          
    Long groupId               
) {}