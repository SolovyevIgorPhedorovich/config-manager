package com.uniikm.configmanager.cache;

/**
 * Снимок статуса задачи применения конфигурации из Redis.
 * Поля уже приведены к типам (deviceId — Long), пригоден для маппинга в DTO.
 */
public record ConfigApplyStatusView(
        String status,
        Long deviceId,
        String startedAt,
        String finishedAt,
        String errorMessage
) {
}
