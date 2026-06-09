package com.uniikm.configmanager.config.enums;

/**
 * Статус отложенного применения конфигурации к устройству, которого не было
 * в сети в момент запроса. Записи в PENDING разбираются фоновым планировщиком
 * {@link com.uniikm.configmanager.config.service.ScheduledConfigApplyService}.
 */
public enum ScheduledApplyStatus {
    /** Ожидает появления устройства в сети. */
    PENDING,
    /** Устройство появилось в сети, команда применения отправлена. */
    APPLIED,
    /** Применение не удалось после исчерпания попыток. */
    FAILED,
    /** Отменено оператором. */
    CANCELLED
}
