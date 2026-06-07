package com.uniikm.configmanager.audit.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record EventLogDto(
        Long id,
        Long userId,
        String userName,
        String eventType,
        String aggregateType,
        Long aggregateId,
        JsonNode payload,
        JsonNode metadata,
        LocalDateTime createdAt
) {}
