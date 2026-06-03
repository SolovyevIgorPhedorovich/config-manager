package com.uniikm.configmanager.integration.dto;

import java.time.LocalDateTime;

import com.uniikm.configmanager.audit.enums.TaskStatus;
import com.uniikm.configmanager.common.dto.ConnectionProtocol;

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
