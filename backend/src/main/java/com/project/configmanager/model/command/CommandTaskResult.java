package com.project.configmanager.model.command;

import java.time.LocalDateTime;

import com.project.configmanager.audit.enums.TaskStatus;

public record CommandTaskResult(
    String taskId,
    String groupTaskId,
    String host,
    ConnectionProtocol protocol,
    TaskStatus status,
    Integer exitCode,
    String output,
    String error,
    LocalDateTime queuedAt,
    LocalDateTime startedAt,
    LocalDateTime finishedAt
) {}
