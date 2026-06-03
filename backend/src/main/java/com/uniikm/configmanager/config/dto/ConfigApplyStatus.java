package com.uniikm.configmanager.config.dto;

public record ConfigApplyStatus(
    String status, 
    String startedAt,
    String finishedAt,
    String errorMessage,
    Integer progress     
) {}