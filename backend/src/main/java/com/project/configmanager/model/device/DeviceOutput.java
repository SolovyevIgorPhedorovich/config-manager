package com.project.configmanager.model.device;

import java.util.List;

public record DeviceOutput(
    Long id,
    String hostname,
    List<String> ips,
    String typeCodeName,
    String osVersion,
    String groupName,
    Boolean isActive
) {}
