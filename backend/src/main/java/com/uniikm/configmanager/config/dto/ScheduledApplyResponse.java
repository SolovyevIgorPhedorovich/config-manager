package com.uniikm.configmanager.config.dto;

import java.time.LocalDateTime;

/** Заявка отложенного применения конфигурации (для отображения в интерфейсе). */
public record ScheduledApplyResponse(
    Long id,
    Long deviceId,
    String deviceHostname,
    Long configVersionId,
    String status,
    String source,
    int attempts,
    String lastError,
    LocalDateTime createdAt,
    LocalDateTime appliedAt
) {}
