package com.project.configmanager.model.command;

import java.util.List;

public record CommandExecutionRequest(
    String command,
    List<DeviceCommandTarget> targets,
    Integer timeoutSeconds
) {}
