package com.uniikm.configmanager.audit.facade;

import java.util.Collection;
import java.util.List;

import com.uniikm.configmanager.audit.dto.EventLogDto;

/**
 * Фасадный интерфейс модуля аудита. Определяет контракт взаимодействия с
 * подсистемой журналирования событий и инкапсулирует сценарии чтения журнала:
 * события устройства, группы устройств и общий журнал с пагинацией.
 *
 * <p>Запись в журнал выполняется через доменные события (см. {@code audit/listener}),
 * поэтому фасад предоставляет только операции чтения.
 */
public interface AuditFacade {

    /** События одного устройства (по убыванию времени). */
    List<EventLogDto> getDeviceLogs(Long deviceId);

    /** События группы устройств. */
    List<EventLogDto> getDeviceLogs(Collection<Long> deviceIds);

    /** Общий журнал событий с пагинацией. */
    List<EventLogDto> getAllLogs(int page, int size);
}
