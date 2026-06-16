package com.uniikm.configmanager.integration.facade;

import com.uniikm.configmanager.integration.dto.CommandExecutionRequest;
import com.uniikm.configmanager.integration.dto.CommandGroupStatus;
import com.uniikm.configmanager.integration.dto.DeviceProbeResult;
import com.uniikm.configmanager.integration.dto.ScanConfig;

/**
 * Фасадный интерфейс модуля интеграции. Единая точка взаимодействия других
 * модулей с подсистемой внешних соединений: сетевое зондирование (доступность,
 * SNMP/SSH-детекция) и асинхронное выполнение команд на устройствах.
 *
 * <p>Принимает и возвращает только контрактные типы интеграции
 * ({@code integration.dto}), не завися от моделей других модулей.
 */
public interface IntegrationFacade {

    /** Тайм-аут одного ping (мс) — для расчёта общих тайм-аутов сканирования. */
    int getPingTimeoutMs();

    /** Доступность узла по IP (ping/TCP). */
    boolean isReachable(String ip);

    /** Зондирование узла: доступность + сбор атрибутов (SNMP/SSH). */
    DeviceProbeResult probe(String ip, ScanConfig config);

    /** Асинхронный запуск группы команд на устройствах. */
    CommandGroupStatus executeAsync(CommandExecutionRequest request);
}
