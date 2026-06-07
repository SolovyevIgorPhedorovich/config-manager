package com.uniikm.configmanager.audit.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.uniikm.configmanager.audit.dto.EventLogDto;
import com.uniikm.configmanager.audit.model.EventLogEntity;
import com.uniikm.configmanager.audit.repository.AuditLogRepository;
import com.uniikm.configmanager.auth.model.User;
import com.uniikm.configmanager.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventQueryService {

    private static final String DEVICE_AGGREGATE = "DEVICE";

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    /** События одного устройства. */
    public List<EventLogDto> getDeviceLogs(Long deviceId) {
        return toDtos(auditLogRepository
                .findByAggregateTypeAndAggregateIdOrderByCreatedAtDesc(DEVICE_AGGREGATE, deviceId));
    }

    /** События группы устройств (например, все устройства текущей вкладки). */
    public List<EventLogDto> getDeviceLogs(Collection<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return List.of();
        }
        return toDtos(auditLogRepository
                .findByAggregateTypeAndAggregateIdInOrderByCreatedAtDesc(DEVICE_AGGREGATE, deviceIds));
    }

    /** Все события (с пагинацией), для общего журнала аудита. */
    public List<EventLogDto> getAllLogs(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 500));
        return toDtos(auditLogRepository.findAllByOrderByCreatedAtDesc(pageable));
    }

    private List<EventLogDto> toDtos(List<EventLogEntity> entities) {
        Map<Long, String> usernames = resolveUsernames(entities);
        return entities.stream()
                .map(e -> new EventLogDto(
                        e.getId(),
                        e.getUserId(),
                        e.getUserId() != null ? usernames.get(e.getUserId()) : null,
                        e.getEventType(),
                        e.getAggregateType(),
                        e.getAggregateId(),
                        e.getPayload(),
                        e.getMetadata(),
                        e.getCreatedAt()))
                .toList();
    }

    private Map<Long, String> resolveUsernames(List<EventLogEntity> entities) {
        List<Long> userIds = entities.stream()
                .map(EventLogEntity::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
    }
}
