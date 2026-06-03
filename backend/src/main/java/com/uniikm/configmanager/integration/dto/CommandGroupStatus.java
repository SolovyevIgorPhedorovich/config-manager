package com.uniikm.configmanager.integration.dto;

import java.util.List;

import com.uniikm.configmanager.audit.enums.TaskStatus;

public record CommandGroupStatus(
    String taskGroupId,
    TaskStatus status,
    int total,
    int completed,
    List<CommandTaskResult> results
) {}
