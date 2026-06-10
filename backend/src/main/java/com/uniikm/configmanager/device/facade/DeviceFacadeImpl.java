package com.uniikm.configmanager.device.facade;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uniikm.configmanager.device.dto.BulkDeleteResponse;
import com.uniikm.configmanager.device.dto.DeleteError;
import com.uniikm.configmanager.device.dto.DeviceRequest;
import com.uniikm.configmanager.device.dto.DeviceResponse;
import com.uniikm.configmanager.device.dto.mapper.DeviceMapper;
import com.uniikm.configmanager.device.model.DeviceInfo;
import com.uniikm.configmanager.device.service.DeviceService;
import com.uniikm.configmanager.integration.service.NetworkProbeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeviceFacadeImpl implements DeviceFacade {

    private final DeviceService deviceService;
    private final DeviceMapper deviceMapper;
    private final NetworkProbeService probeService;

    /** Верхняя граница ожидания пинга одного устройства (ICMP + перебор TCP-портов). */
    private static final long PING_TIMEOUT_MS = 8000;

    /** Отдельный пул для параллельного пинга (IO-bound), демонизирован. */
    private final ExecutorService pingPool = Executors.newFixedThreadPool(
            Math.max(8, Runtime.getRuntime().availableProcessors() * 4),
            r -> {
                Thread t = new Thread(r, "device-ping");
                t.setDaemon(true);
                return t;
            });

    @Override
    public List<DeviceResponse> getAll() {
        return deviceService.getAll()
                .stream()
                .map(deviceMapper::toResponse)
                .toList();
    }

    @Override
    public DeviceResponse getById(Long id) {
        return deviceMapper.toResponse(
                deviceService.getById(id)
        );
    }

    @Override
    public DeviceResponse create(DeviceRequest request) {
        return deviceMapper.toResponse(
                deviceService.create(request, request.actor())
        );
    }

    @Override
    public DeviceResponse update(Long id, DeviceRequest request) {
        return deviceMapper.toResponse(
                deviceService.update(id, request, request.actor())
        );
    }

    @Override
    public void delete(Long id) {
        deviceService.delete(id);
    }
    
    @Override
    public BulkDeleteResponse bulkDelete(List<Long> ids) {
        int successCount = 0;
        int failCount = 0;
        List<DeleteError> errors = new ArrayList<>();
        
        for (Long id : ids) {
            try {
                deviceService.delete(id);
                successCount++;
            } catch (Exception e) {
                failCount++;
                errors.add(deviceMapper.toDeleteError(id, e.getMessage()));
            }
        }
        
        return deviceMapper.toBulkDeleteResponse(successCount, failCount, errors);
    }

    @Override
    public DeviceInfo getDeviceEntity(Long id) {
        return deviceService.getById(id);
    }

    @Override
    public List<DeviceInfo> getDeviceEntities(List<Long> ids) {
        return deviceService.getAllByIds(ids);
    }

    /**
     * Кэш фактической доступности устройств (id → online). Заполняется фоновым
     * свипом {@link #refreshReachability()}, эндпоинт {@code /status} отдаёт его
     * мгновенно. Так стоимость пинга = O(устройства) независимо от числа клиентов
     * и не зависит от частоты опроса фронтом.
     */
    private volatile Map<Long, Boolean> reachabilityCache = Map.of();

    @Override
    public Map<Long, Boolean> getReachability() {
        return reachabilityCache;
    }

    /**
     * Фоновый пинг-свип: обновляет кэш доступности. fixedDelay — следующий запуск
     * стартует только после завершения предыдущего, поэтому свипы не накладываются
     * даже если часть хостов висит до таймаута.
     */
    @Scheduled(
        initialDelayString = "${devices.status.initial-delay-ms:3000}",
        fixedDelayString   = "${devices.status.refresh-ms:15000}"
    )
    @Transactional(readOnly = true)  // открываем сессию Hibernate — иначе ленивая DeviceInfo.ips падает вне веб-запроса
    public void refreshReachability() {
        long t0 = System.currentTimeMillis();
        Map<Long, Boolean> fresh = computeReachability();
        reachabilityCache = fresh;
        long dur = System.currentTimeMillis() - t0;
        long online = fresh.values().stream().filter(Boolean::booleanValue).count();
        // Эндпоинт всё равно отдаёт кэш мгновенно; длинный свип лишь означает, что
        // часть хостов недоступна (висят до таймаута) и кэш обновляется реже.
        if (dur > 3000) {
            log.warn("Пинг-свип затянулся: {} устройств ({} online) за {} мс — часть хостов недоступна",
                    fresh.size(), online, dur);
        } else {
            log.debug("Пинг-свип: {} устройств ({} online) за {} мс", fresh.size(), online, dur);
        }
    }

    private Map<Long, Boolean> computeReachability() {
        List<DeviceResponse> devices = getAll();

        // Пингуем все устройства параллельно; каждый — со своим таймаутом,
        // чтобы один «зависший» хост не задерживал общий ответ.
        Map<Long, CompletableFuture<Boolean>> futures = new LinkedHashMap<>();
        for (DeviceResponse d : devices) {
            String ip = (d.ips() != null && !d.ips().isEmpty()) ? d.ips().get(0) : null;
            if (ip == null || ip.isBlank()) {
                futures.put(d.id(), CompletableFuture.completedFuture(false));
            } else {
                futures.put(d.id(), CompletableFuture
                        .supplyAsync(() -> probeService.isReachable(ip), pingPool)
                        .completeOnTimeout(false, PING_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        .exceptionally(ex -> false));
            }
        }

        Map<Long, Boolean> result = new LinkedHashMap<>();
        futures.forEach((id, future) -> result.put(id, future.join()));
        return result;
    }
}