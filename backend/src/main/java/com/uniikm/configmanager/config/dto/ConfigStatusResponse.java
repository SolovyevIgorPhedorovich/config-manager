package com.uniikm.configmanager.config.dto;

public record ConfigStatusResponse(
    String taskGroupId,
    String status,
    Long deviceId,
    String startedAt,
    String finishedAt,
    String errorMessage
) {}