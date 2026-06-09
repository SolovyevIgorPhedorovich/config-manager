package com.uniikm.configmanager.device.facade;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.uniikm.configmanager.device.dto.BulkDeleteResponse;
import com.uniikm.configmanager.device.dto.DeleteError;
import com.uniikm.configmanager.device.dto.DeviceRequest;
import com.uniikm.configmanager.device.dto.DeviceResponse;
import com.uniikm.configmanager.device.dto.mapper.DeviceMapper;
import com.uniikm.configmanager.device.model.DeviceInfo;
import com.uniikm.configmanager.device.service.DeviceService;
import com.uniikm.configmanager.integration.service.NetworkProbeService;

import lombok.RequiredArgsConstructor;

@Service
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

    @Override
    public Map<Long, Boolean> getReachability() {
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