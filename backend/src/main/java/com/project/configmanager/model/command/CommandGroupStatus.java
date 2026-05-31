package com.project.configmanager.model.command;

import java.util.List;

import com.project.configmanager.audit.enums.TaskStatus;

public record CommandGroupStatus(
    String taskGroupId,
    TaskStatus status,
    int total,
    int completed,
    List<CommandTaskResult> results
) {}
