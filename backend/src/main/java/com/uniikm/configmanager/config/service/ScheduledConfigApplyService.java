package com.uniikm.configmanager.config.service;

import com.uniikm.configmanager.common.crypto.SecretCipher;
import com.uniikm.configmanager.config.dto.DeviceCredentials;
import com.uniikm.configmanager.config.dto.ScheduledApplyResponse;
import com.uniikm.configmanager.config.enums.ScheduledApplyStatus;
import com.uniikm.configmanager.config.model.ConfigVersion;
import com.uniikm.configmanager.config.model.ScheduledConfigApply;
import com.uniikm.configmanager.config.repository.ScheduledConfigApplyRepository;
import com.uniikm.configmanager.device.model.DeviceInfo;
import com.uniikm.configmanager.device.repository.DeviceRepository;
import com.uniikm.configmanager.integration.service.NetworkProbeService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Очередь отложенного применения конфигурации к устройствам, которых не было
 * в сети в момент запроса. Фоновый поллер периодически проверяет доступность
 * PENDING-устройств и применяет конфиг, как только устройство появляется в сети.
 *
 * Учётные данные хранятся зашифрованно ({@link SecretCipher}) — это сознательное
 * решение, чтобы применение происходило автоматически без участия оператора.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledConfigApplyService {

    private final ScheduledConfigApplyRepository repository;
    private final SecretCipher cipher;
    private final NetworkProbeService probeService;
    private final DeviceRepository deviceRepository;
    private final ConfigVersionService configVersionService;
    private final ConfigOrchestrationService orchestrationService;
    private final TaskScheduler scanTaskScheduler;

    // Self-инъекция: чтобы @Transactional processOne(...) вызывался через прокси
    // (иначе при self-invocation транзакция не открывается и ленивые поля недоступны).
    @Autowired
    @Lazy
    private ScheduledConfigApplyService self;

    @Value("${config-apply.queue.poll-interval-seconds:60}")
    private long pollIntervalSeconds;

    @Value("${config-apply.queue.max-attempts:10}")
    private int maxAttempts;

    @PostConstruct
    void startPoller() {
        Duration interval = Duration.ofSeconds(pollIntervalSeconds);
        scanTaskScheduler.scheduleWithFixedDelay(this::flushPending, interval);
        log.info("Scheduled-config-apply poller запущен с интервалом {}с", pollIntervalSeconds);
    }

    /** Поставить применение версии в очередь (устройство офлайн). */
    @Transactional
    public ScheduledConfigApply enqueue(DeviceInfo device, ConfigVersion version,
                                        DeviceCredentials creds, String source, String batchId) {
        ScheduledConfigApply entry = ScheduledConfigApply.builder()
                .deviceId(device.getId())
                .configVersionId(version.getId())
                .username(creds.getUsername())
                .encPassword(cipher.encrypt(creds.getPassword()))
                .encCommunity(cipher.encrypt(creds.getCommunity()))
                .port(creds.getPort())
                .status(ScheduledApplyStatus.PENDING)
                .source(source)
                .batchId(batchId)
                .attempts(0)
                .build();
        return repository.save(entry);
    }

    /**
     * Перебирает PENDING-заявки. Выполняется на потоке планировщика без транзакции;
     * каждая заявка обрабатывается в отдельной транзакции через прокси (self.processOne),
     * чтобы ленивые поля устройства были доступны и одна «зависшая» заявка не держала
     * длинную транзакцию.
     */
    public void flushPending() {
        List<ScheduledConfigApply> pending = repository.findByStatus(ScheduledApplyStatus.PENDING);
        if (pending.isEmpty()) return;

        log.debug("Очередь отложенного применения: {} ожидающих заявок", pending.size());
        for (ScheduledConfigApply entry : pending) {
            try {
                self.processOne(entry.getId());
            } catch (Exception e) {
                log.warn("Ошибка обработки заявки {} (device {}): {}",
                        entry.getId(), entry.getDeviceId(), e.getMessage());
            }
        }
    }

    /** Обработка одной заявки в собственной транзакции. */
    @Transactional
    public void processOne(Long entryId) {
        ScheduledConfigApply entry = repository.findById(entryId).orElse(null);
        if (entry == null || entry.getStatus() != ScheduledApplyStatus.PENDING) return;

        try {
            DeviceInfo device = deviceRepository.findById(entry.getDeviceId()).orElse(null);
            if (device == null) {
                log.warn("Заявка {}: устройство {} не найдено — помечаю FAILED", entry.getId(), entry.getDeviceId());
                entry.setStatus(ScheduledApplyStatus.FAILED);
                entry.setLastError("Устройство удалено");
                repository.save(entry);
                return;
            }

            String ip = device.getIps().isEmpty() ? device.getHostname() : device.getIps().get(0).getIp();
            if (!probeService.isReachable(ip)) {
                // Всё ещё офлайн — недоступность не считается неудачной попыткой, ждём дальше.
                return;
            }

            ConfigVersion version = configVersionService.getVersionById(entry.getConfigVersionId());
            DeviceCredentials creds = new DeviceCredentials();
            creds.setUsername(entry.getUsername());
            creds.setPassword(cipher.decrypt(entry.getEncPassword()));
            creds.setCommunity(cipher.decrypt(entry.getEncCommunity()));
            creds.setPort(entry.getPort());

            orchestrationService.executeApply(device, version, creds);
            entry.setStatus(ScheduledApplyStatus.APPLIED);
            entry.setAppliedAt(LocalDateTime.now());
            entry.setLastError(null);
            repository.save(entry);
            log.info("Отложенное применение выполнено: заявка {}, устройство {} ({}), версия {}",
                    entry.getId(), device.getId(), ip, version.getId());
        } catch (Exception e) {
            entry.setAttempts(entry.getAttempts() + 1);
            entry.setLastError(e.getMessage());
            if (entry.getAttempts() >= maxAttempts) {
                entry.setStatus(ScheduledApplyStatus.FAILED);
                log.warn("Заявка {} помечена FAILED после {} попыток", entry.getId(), entry.getAttempts());
            }
            repository.save(entry);
            log.warn("Ошибка применения заявки {}: {}", entry.getId(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<ScheduledApplyResponse> listPending() {
        return repository.findByStatusOrderByCreatedAtDesc(ScheduledApplyStatus.PENDING).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ScheduledApplyResponse> listByDevice(Long deviceId) {
        return repository.findByDeviceIdAndStatus(deviceId, ScheduledApplyStatus.PENDING).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void cancel(Long id) {
        ScheduledConfigApply entry = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Заявка не найдена: " + id));
        entry.setStatus(ScheduledApplyStatus.CANCELLED);
        repository.save(entry);
        log.info("Заявка отложенного применения {} отменена", id);
    }

    private ScheduledApplyResponse toResponse(ScheduledConfigApply e) {
        String hostname = deviceRepository.findById(e.getDeviceId())
                .map(DeviceInfo::getHostname).orElse(null);
        return new ScheduledApplyResponse(
                e.getId(), e.getDeviceId(), hostname, e.getConfigVersionId(),
                e.getStatus().name(), e.getSource(), e.getAttempts(), e.getLastError(),
                e.getCreatedAt(), e.getAppliedAt());
    }
}
