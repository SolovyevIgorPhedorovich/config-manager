package com.uniikm.configmanager.config.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record TemplateResponse(
        Long id,
        String name,
        String description,
        JsonNode content,
        Boolean isActive,
        String createdBy,
        LocalDateTime createdAt,
        int deviceCount
) {}
