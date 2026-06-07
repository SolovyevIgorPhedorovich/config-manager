package com.uniikm.configmanager.device.service;

import com.uniikm.configmanager.common.crypto.SecretCipher;
import com.uniikm.configmanager.device.dto.ScanScheduleDto;
import com.uniikm.configmanager.device.model.ScanScheduleConfig;
import com.uniikm.configmanager.device.repository.ScanScheduleRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanScheduleService {

    private final ScanScheduleRepository repository;
    private final NetworkScannerService scannerService;
    private final TaskScheduler scanTaskScheduler;
    private final SecretCipher cipher;
    private final ScanCredentialService credentialService;

    private final ConcurrentHashMap<Long, ScheduledFuture<?>> activeTasks = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadSchedulesOnStartup() {
        repository.findAllByEnabledTrue().forEach(this::scheduleTask);
        log.info("Loaded {} active scan schedules", activeTasks.size());
    }

    public List<ScanScheduleConfig> getAll() {
        return repository.findAll();
    }

    public ScanScheduleConfig getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + id));
    }

    @Transactional
    public ScanScheduleConfig create(ScanScheduleDto dto) {
        ScanScheduleConfig config = ScanScheduleConfig.builder()
                .name(dto.name())
                .subnet(dto.subnet())
                .mask(dto.mask())
                .port(dto.port())
                .community(dto.community())
                .snmpVersion(dto.snmpVersion())
                .scanMode(dto.scanMode())
                .cronExpression(dto.cronExpression())
                .enabled(dto.enabled())
                .credentialId(dto.credentialId())
                .sshUsername(dto.sshUsername())
                .sshPassword(cipher.encrypt(dto.sshPassword()))
                .winrmUsername(dto.winrmUsername())
                .winrmPassword(cipher.encrypt(dto.winrmPassword()))
                .snmpSecurityName(dto.snmpSecurityName())
                .snmpAuthProtocol(dto.snmpAuthProtocol())
                .snmpAuthPassword(cipher.encrypt(dto.snmpAuthPassword()))
                .snmpPrivProtocol(dto.snmpPrivProtocol())
                .snmpPrivPassword(cipher.encrypt(dto.snmpPrivPassword()))
                .build();
        ScanScheduleConfig saved = repository.save(config);
        if (saved.isEnabled() && saved.getCronExpression() != null) {
            scheduleTask(saved);
        }
        return saved;
    }

    @Transactional
    public ScanScheduleConfig update(Long id, ScanScheduleDto dto) {
        ScanScheduleConfig config = getById(id);
        config.setName(dto.name());
        config.setSubnet(dto.subnet());
        config.setMask(dto.mask());
        config.setPort(dto.port());
        config.setCommunity(dto.community());
        config.setSnmpVersion(dto.snmpVersion());
        config.setScanMode(dto.scanMode());
        config.setCronExpression(dto.cronExpression());
        config.setEnabled(dto.enabled());
        config.setCredentialId(dto.credentialId());
        config.setSshUsername(dto.sshUsername());
        config.setWinrmUsername(dto.winrmUsername());
        config.setSnmpSecurityName(dto.snmpSecurityName());
        config.setSnmpAuthProtocol(dto.snmpAuthProtocol());
        config.setSnmpPrivProtocol(dto.snmpPrivProtocol());
        // Пустой пароль в запросе = «не менять» (чтобы не требовать повторного ввода при редактировании)
        if (dto.sshPassword() != null && !dto.sshPassword().isBlank()) {
            config.setSshPassword(cipher.encrypt(dto.sshPassword()));
        }
        if (dto.winrmPassword() != null && !dto.winrmPassword().isBlank()) {
            config.setWinrmPassword(cipher.encrypt(dto.winrmPassword()));
        }
        if (dto.snmpAuthPassword() != null && !dto.snmpAuthPassword().isBlank()) {
            config.setSnmpAuthPassword(cipher.encrypt(dto.snmpAuthPassword()));
        }
        if (dto.snmpPrivPassword() != null && !dto.snmpPrivPassword().isBlank()) {
            config.setSnmpPrivPassword(cipher.encrypt(dto.snmpPrivPassword()));
        }

        cancelTask(id);
        ScanScheduleConfig saved = repository.save(config);
        if (saved.isEnabled() && saved.getCronExpression() != null) {
            scheduleTask(saved);
        }
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        cancelTask(id);
        repository.deleteById(id);
    }

    public ResponseEntity<Map<String, Object>> runNow(Long id) {
        ScanScheduleConfig config = getById(id);
        ScanCredentialService.ResolvedCreds creds = credsFor(config);
        return scannerService.startScan(
                config.getSubnet(), config.getMask(), config.getPort(),
                config.getCommunity(), config.getSnmpVersion(), config.getScanMode(),
                config.getSnmpSecurityName(), config.getSnmpAuthProtocol(), cipher.decrypt(config.getSnmpAuthPassword()),
                config.getSnmpPrivProtocol(), cipher.decrypt(config.getSnmpPrivPassword()),
                creds.sshUsername(), creds.sshPassword(),
                creds.winrmUsername(), creds.winrmPassword()
        );
    }

    /** Креды для запуска: из профиля доступа (если задан) либо из собственных полей расписания. */
    private ScanCredentialService.ResolvedCreds credsFor(ScanScheduleConfig config) {
        if (config.getCredentialId() != null) {
            return credentialService.resolve(config.getCredentialId());
        }
        return new ScanCredentialService.ResolvedCreds(
                config.getSshUsername(), cipher.decrypt(config.getSshPassword()),
                config.getWinrmUsername(), cipher.decrypt(config.getWinrmPassword()));
    }

    private void scheduleTask(ScanScheduleConfig config) {
        if (config.getCronExpression() == null || config.getCronExpression().isBlank()) return;
        try {
            ScheduledFuture<?> future = scanTaskScheduler.schedule(
                    () -> runScheduledScan(config.getId()),
                    new CronTrigger(config.getCronExpression())
            );
            activeTasks.put(config.getId(), future);
            log.info("Scheduled scan '{}' [{}] cron='{}'", config.getName(), config.getId(), config.getCronExpression());
        } catch (Exception e) {
            log.error("Failed to schedule task for id {}: {}", config.getId(), e.getMessage());
        }
    }

    private void cancelTask(Long id) {
        ScheduledFuture<?> future = activeTasks.remove(id);
        if (future != null) {
            future.cancel(false);
            log.info("Cancelled scan schedule id={}", id);
        }
    }

    private void runScheduledScan(Long id) {
        ScanScheduleConfig config = repository.findById(id).orElse(null);
        if (config == null || !config.isEnabled()) return;
        log.info("Running scheduled scan '{}' id={}", config.getName(), id);
        try {
            ScanCredentialService.ResolvedCreds creds = credsFor(config);
            scannerService.startScan(
                    config.getSubnet(), config.getMask(), config.getPort(),
                    config.getCommunity(), config.getSnmpVersion(), config.getScanMode(),
                    config.getSnmpSecurityName(), config.getSnmpAuthProtocol(), cipher.decrypt(config.getSnmpAuthPassword()),
                    config.getSnmpPrivProtocol(), cipher.decrypt(config.getSnmpPrivPassword()),
                    creds.sshUsername(), creds.sshPassword(),
                    creds.winrmUsername(), creds.winrmPassword()
            );
            config.setLastRunAt(LocalDateTime.now());
            config.setLastRunStatus("success");
        } catch (Exception e) {
            log.error("Scheduled scan failed for id {}: {}", id, e.getMessage());
            config.setLastRunAt(LocalDateTime.now());
            config.setLastRunStatus("failed");
        }
        repository.save(config);
    }
}
