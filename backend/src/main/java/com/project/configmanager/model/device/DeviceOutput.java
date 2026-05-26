package com.project.configmanager.model.device;

import java.util.List;

public record DeviceOutput(
    Long id,
    String hostname,
    List<String> ips,
    Integer typeCode,
    String type,
    String osVersion,
    String groupName,
    Boolean isActive
) {}
