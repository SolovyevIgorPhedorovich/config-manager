package com.uniikm.configmanager.config.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class TemplateApplyRequest {

    /** Список устройств для применения. Если null — применяется ко всем привязанным устройствам. */
    private List<Long> deviceIds;

    /**
     * Переменные для подстановки в шаблон. Поддерживаемый синтаксис: {{variable_name}}.
     * Встроенные переменные (подставляются автоматически): {{device.hostname}}, {{device.ip}}, {{device.type}}.
     */
    private Map<String, String> variables;

    /** Учётные данные для каждого устройства (deviceId → credentials). */
    @NotNull
    private Map<Long, DeviceCredentials> credentials;
}
