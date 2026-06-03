package com.uniikm.configmanager.integration.dto;

import java.util.List;

import com.uniikm.configmanager.common.dto.DeviceCommandTarget;

public record CommandExecutionRequest(
    String command,
    List<DeviceCommandTarget> targets,
    Integer timeoutSeconds,
    String groupTaskId
) {}
