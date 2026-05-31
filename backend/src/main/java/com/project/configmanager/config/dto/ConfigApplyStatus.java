package com.project.configmanager.config.dto;

public record ConfigApplyStatus(
    String status,        // "IN_PROGRESS", "SUCCESS", "FAILED"
    String startedAt,
    String finishedAt,
    String errorMessage,
    Integer progress     
) {}