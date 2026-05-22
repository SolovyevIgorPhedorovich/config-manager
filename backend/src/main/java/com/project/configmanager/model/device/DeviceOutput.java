package com.project.configmanager.model.device;

import java.util.List;

public record DeviceOutput(
    Long id,
    String hostname,
    List<String> ips,
    String type,
    String osVersion,
    String groupName,
    Boolean isActive
) {}
