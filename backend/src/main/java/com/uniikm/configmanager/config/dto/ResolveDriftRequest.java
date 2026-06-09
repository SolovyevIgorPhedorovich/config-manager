package com.uniikm.configmanager.config.dto;

import lombok.Data;

/**
 * Запрос на разрешение расхождения конфигурации (drift).
 * resolution:
 *   ACCEPT_DEVICE   — принять фактическую конфигурацию устройства как активную;
 *   REAPPLY_STORED  — повторно применить сохранённую активную конфигурацию на устройство.
 * credentials нужны только для REAPPLY_STORED.
 */
@Data
public class ResolveDriftRequest {
    private String resolution;
    private DeviceCredentials credentials;
}
