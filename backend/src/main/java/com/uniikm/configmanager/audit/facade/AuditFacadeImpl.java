package com.uniikm.configmanager.audit.facade;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Component;

import com.uniikm.configmanager.audit.dto.EventLogDto;
import com.uniikm.configmanager.audit.service.EventQueryService;

import lombok.RequiredArgsConstructor;

/**
 * Реализация фасада модуля аудита. Делегирует чтение журнала
 * прикладному сервису {@link EventQueryService}.
 */
@Component
@RequiredArgsConstructor
public class AuditFacadeImpl implements AuditFacade {

    private final EventQueryService eventQueryService;

    @Override
    public List<EventLogDto> getDeviceLogs(Long deviceId) {
        return eventQueryService.getDeviceLogs(deviceId);
    }

    @Override
    public List<EventLogDto> getDeviceLogs(Collection<Long> deviceIds) {
        return eventQueryService.getDeviceLogs(deviceIds);
    }

    @Override
    public List<EventLogDto> getAllLogs(int page, int size) {
        return eventQueryService.getAllLogs(page, size);
    }
}
