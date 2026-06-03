package com.uniikm.configmanager.config.dto;

public record ConfigCompareRequest(
    Long oldVersionId,
    Long newVersionId
) {}