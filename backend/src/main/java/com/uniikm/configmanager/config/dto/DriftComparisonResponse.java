package com.uniikm.configmanager.config.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Сравнение конфигураций для разрешения расхождения:
 * stored — сохранённая активная (намеренная) версия,
 * actual — захваченная фактическая конфигурация устройства.
 */
public record DriftComparisonResponse(
    JsonNode stored,
    JsonNode actual
) {}
