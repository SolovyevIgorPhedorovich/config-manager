package com.uniikm.configmanager.device.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniikm.configmanager.device.dto.DeviceResponse;
import com.uniikm.configmanager.device.dto.ScanResultEntry;
import com.uniikm.configmanager.device.dto.mapper.DeviceMapper;
import com.uniikm.configmanager.device.enums.DeviceType;
import com.uniikm.configmanager.device.model.DeviceGroup;
import com.uniikm.configmanager.device.model.DeviceIP;
import com.uniikm.configmanager.device.model.DeviceInfo;
import com.uniikm.configmanager.device.model.DeviceOS;
import com.uniikm.configmanager.device.repository.DeviceGroupRepository;
import com.uniikm.configmanager.device.repository.DeviceOSRepository;
import com.uniikm.configmanager.device.repository.DeviceRepository;
import com.uniikm.configmanager.integration.dto.DeviceProbeResult;
import com.uniikm.configmanager.integration.dto.ScanConfig;
import com.uniikm.configmanager.integration.service.NetworkProbeService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class NetworkScannerService {

    private final DeviceRepository deviceRepo;
    private final DeviceGroupRepository deviceGroupRepo;
    private final DeviceOSRepository deviceOSRepo;
    private final DeviceMapper deviceMapper;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final NetworkProbeService networkProbeService;

    @Value("${network-scan.redis-key-prefix:network-scan}")
    private String redisKeyPrefix;

    @Value("${network-scan.result-ttl:PT5M}")
    private Duration resultTtl;

    @Value("${network-scan.max-hosts:256}")
    private int maxHosts;

    @Value("${network-scan.thread-pool-size:20}")
    private int threadPoolSize;

    @Value("${network-scan.ping-enabled:true}")
    private boolean pingEnabled;

    public NetworkScannerService(DeviceRepository deviceRepo,
                                 DeviceGroupRepository deviceGroupRepo,
                                 DeviceOSRepository deviceOSRepo,
                                 DeviceMapper deviceMapper,
                                 ObjectMapper objectMapper,
                                 StringRedisTemplate redisTemplate,
                                 NetworkProbeService networkProbeService) {
        this.deviceRepo = deviceRepo;
        this.deviceGroupRepo = deviceGroupRepo;
        this.deviceOSRepo = deviceOSRepo;
        this.deviceMapper = deviceMapper;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.networkProbeService = networkProbeService;
    }

    public ResponseEntity<Map<String, Object>> startScan(
            String ipaddr, int mask, int port,
            String community, String snmpv, String scanMode,
            String sshUsername, String sshPassword,
            String winrmUsername, String winrmPassword) {

        if (mask > 24) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Максимальная маска для сканирования: /24 (" + maxHosts + " хостов)"
            ));
        }

        ScanConfig config = new ScanConfig(
                port, community, snmpv,
                sshUsername, sshPassword,
                winrmUsername, winrmPassword,
                pingEnabled
        );
        String taskId = UUID.randomUUID().toString();

        CompletableFuture<List<ScanResultEntry>> future = scanAsync(ipaddr, mask, scanMode, config);
        future.thenAccept(results -> saveScanResult(taskId, results))
              .exceptionally(ex -> {
                  log.error("Scan failed for taskId {}: {}", taskId, ex.getMessage());
                  saveScanResult(taskId, List.of());
                  return null;
              });

        return ResponseEntity.ok(Map.of(
                "taskId", taskId,
                "status", "started",
                "message", "Сканирование запущено. Используйте /api/v1/devices/scan/status?taskId=" + taskId
        ));
    }

    public ResponseEntity<Object> getResult(String taskId) {
        List<ScanResultEntry> results = getScanResult(taskId);
        if (results == null) {
            return ResponseEntity.ok(Map.of(
                    "status", "running",
                    "message", "Сканирование ещё не завершено"
            ));
        }
        redisTemplate.delete(taskKey(taskId));

        long newCount      = results.stream().filter(r -> "NEW".equals(r.scanStatus())).count();
        long existingCount = results.stream().filter(r -> "EXISTING".equals(r.scanStatus())).count();
        long updatedCount  = results.stream().filter(r -> "UPDATED".equals(r.scanStatus())).count();

        return ResponseEntity.ok(Map.of(
                "status",        "completed",
                "results",       results,
                "count",         results.size(),
                "newCount",      newCount,
                "existingCount", existingCount,
                "updatedCount",  updatedCount
        ));
    }

    @Async("taskExecutor")
    public CompletableFuture<List<ScanResultEntry>> scanAsync(
            String ipStart, int mask, String scanMode, ScanConfig config) {
        try {
            validateMask(mask);
            int hostsCount = (int) Math.pow(2, 32 - mask);
            if (hostsCount > maxHosts) {
                throw new IllegalArgumentException("Слишком большая сеть! Максимум хостов: " + maxHosts);
            }

            InetAddress startIp = InetAddress.getByName(ipStart);
            byte[] ipBytes = startIp.getAddress();

            ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
            List<CompletableFuture<DeviceProbeResult>> probeFutures = new ArrayList<>();

            for (int i = 0; i < hostsCount; i++) {
                final String ipStr = incrementIp(ipBytes, i);
                if (isNetworkOrBroadcast(ipStr, mask)) continue;

                probeFutures.add(CompletableFuture.supplyAsync(
                        () -> networkProbeService.probe(ipStr, config), executor));
            }

            int futureTimeoutMs = networkProbeService.getPingTimeoutMs() + 15000;
            List<DeviceProbeResult> probeResults = probeFutures.stream()
                    .map(f -> {
                        try { return f.get(futureTimeoutMs, TimeUnit.MILLISECONDS); }
                        catch (Exception e) { return null; }
                    })
                    .filter(Objects::nonNull)
                    .toList();

            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }

            log.info("Phase 1 complete: {} hosts detected", probeResults.size());

            List<ScanResultEntry> results = new ArrayList<>();
            for (DeviceProbeResult probe : probeResults) {
                try {
                    int typeCode = detectDeviceType(probe.sysDescr());
                    if (!matchesScanMode(typeCode, scanMode)) continue;

                    Optional<DeviceInfo> existing = deviceRepo.findByIp(probe.ip());
                    if (existing.isPresent()) {
                        DeviceInfo device = existing.get();
                        boolean changed = updateDeviceFromProbe(device, probe);
                        if (changed) deviceRepo.save(device);
                        DeviceResponse response = deviceMapper.toResponse(device);
                        results.add(new ScanResultEntry(response, changed ? "UPDATED" : "EXISTING"));
                    } else {
                        DeviceInfo newDevice = createDeviceFromProbe(probe, typeCode);
                        DeviceResponse response = deviceMapper.toResponse(deviceRepo.save(newDevice));
                        results.add(new ScanResultEntry(response, "NEW"));
                    }
                } catch (Exception e) {
                    log.warn("DB processing error for {}: {}", probe.ip(), e.getMessage());
                }
            }

            log.info("Scan done. Total: {}, New: {}, Existing: {}, Updated: {}",
                    results.size(),
                    results.stream().filter(r -> "NEW".equals(r.scanStatus())).count(),
                    results.stream().filter(r -> "EXISTING".equals(r.scanStatus())).count(),
                    results.stream().filter(r -> "UPDATED".equals(r.scanStatus())).count());

            return CompletableFuture.completedFuture(results);

        } catch (Exception e) {
            log.error("Scan failed", e);
            throw new RuntimeException("Ошибка сканирования: " + e.getMessage(), e);
        }
    }

    // ── Domain: определение типа устройства по sysDescr ──────────────────────

    private int detectDeviceType(String sysDescr) {
        if (sysDescr == null) return DeviceType.WINDOWS.getCode();
        String lower = sysDescr.toLowerCase();
        if (lower.contains("windows"))
            return DeviceType.WINDOWS.getCode();
        if (lower.contains("linux") || lower.contains("ubuntu") || lower.contains("debian")
                || lower.contains("centos") || lower.contains("redhat") || lower.contains("fedora"))
            return DeviceType.LINUX.getCode();
        if (lower.contains("canon") || lower.contains("kyocera") || lower.contains("xerox")
                || lower.contains("ricoh") || lower.contains("brother") || lower.contains("epson")
                || (lower.contains("hp") && lower.contains("laserjet")))
            return DeviceType.МФУ.getCode();
        if (lower.contains("cisco") || lower.contains("ios software"))
            return DeviceType.CISCO.getCode();
        if (lower.contains("proxmox") || lower.contains("vmware") || lower.contains("esxi"))
            return DeviceType.PROXMOX.getCode();
        return DeviceType.WINDOWS.getCode();
    }

    private boolean matchesScanMode(int typeCode, String scanMode) {
        if (scanMode == null || "all".equalsIgnoreCase(scanMode)) return true;
        return switch (scanMode.toLowerCase()) {
            case "windows", "pc" -> typeCode == DeviceType.WINDOWS.getCode();
            case "linux", "vm"   -> typeCode == DeviceType.LINUX.getCode() || typeCode == DeviceType.PROXMOX.getCode();
            case "mfu"           -> typeCode == DeviceType.МФУ.getCode();
            case "cisco"         -> typeCode == DeviceType.CISCO.getCode();
            default              -> true;
        };
    }

    // ── Domain: создание/обновление сущностей ─────────────────────────────────

    private boolean updateDeviceFromProbe(DeviceInfo device, DeviceProbeResult probe) {
        boolean changed = false;
        if (!probe.hostname().equals(device.getHostname())) {
            device.setHostname(probe.hostname());
            changed = true;
        }
        String currentOsName = device.getOsVersion() != null ? device.getOsVersion().getName() : null;
        String newOsName = truncate(probe.sysDescr(), 250);
        if (!newOsName.equals(currentOsName)) {
            device.setOsVersion(findOrCreateDeviceOS(probe));
            changed = true;
        }
        return changed;
    }

    private DeviceInfo createDeviceFromProbe(DeviceProbeResult probe, int typeCode) {
        DeviceGroup group = findOrCreateGroup("Auto-discovered");
        DeviceOS os = findOrCreateDeviceOS(probe);

        DeviceInfo device = new DeviceInfo();
        device.setHostname(probe.hostname());
        device.setTypeCode(typeCode);
        device.setIsActive(true);
        device.setGroup(group);
        device.setOsVersion(os);

        DeviceIP ip = new DeviceIP();
        ip.setIp(probe.ip());
        ip.setIsPrimary(true);
        ip.setDevice(device);
        device.setIps(new ArrayList<>(List.of(ip)));

        return device;
    }

    private DeviceGroup findOrCreateGroup(String name) {
        return deviceGroupRepo.findByName(name).orElseGet(() -> {
            DeviceGroup g = new DeviceGroup();
            g.setName(name);
            g.setDescription("Устройства, обнаруженные при сканировании сети");
            return deviceGroupRepo.save(g);
        });
    }

    private DeviceOS findOrCreateDeviceOS(DeviceProbeResult probe) {
        String osName = truncate(probe.sysDescr() != null ? probe.sysDescr().trim() : "Unknown", 250);
        return deviceOSRepo.findByName(osName).orElseGet(() -> {
            DeviceOS os = new DeviceOS();
            os.setName(osName);
            if (probe.vendor() != null) os.setVendor(truncate(probe.vendor(), 255));
            if (probe.model() != null)  os.setModel(truncate(probe.model(), 200));
            return deviceOSRepo.save(os);
        });
    }

    // ── IP helpers ────────────────────────────────────────────────────────────

    private String incrementIp(byte[] baseIp, int offset) {
        byte[] ip = baseIp.clone();
        int carry = offset;
        for (int j = 3; j >= 0 && carry > 0; j--) {
            int sum = (ip[j] & 0xFF) + carry;
            ip[j] = (byte) (sum % 256);
            carry = sum / 256;
        }
        return (ip[0] & 0xFF) + "." + (ip[1] & 0xFF) + "." + (ip[2] & 0xFF) + "." + (ip[3] & 0xFF);
    }

    private boolean isNetworkOrBroadcast(String ipStr, int mask) {
        if (mask <= 0 || mask >= 32) return false;
        try {
            InetAddress addr = InetAddress.getByName(ipStr);
            byte[] net = getNetworkAddressBytes(addr.getAddress().clone(), mask);
            byte[] brd = getBroadcastAddressBytes(addr.getAddress().clone(), mask);
            return Arrays.equals(net, addr.getAddress()) || Arrays.equals(brd, addr.getAddress());
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] getNetworkAddressBytes(byte[] ip, int mask) {
        int full = mask / 8, bits = mask % 8;
        for (int i = full; i < 4; i++) {
            if (i == full && bits > 0) ip[i] &= (byte) (0xFF << (8 - bits));
            else if (i > full)        ip[i] = 0;
        }
        return ip;
    }

    private byte[] getBroadcastAddressBytes(byte[] ip, int mask) {
        if (mask == 32) return ip;
        int full = mask / 8, bits = mask % 8;
        for (int i = full; i < 4; i++) {
            if (i == full && bits > 0) ip[i] |= (byte) (0xFF >> bits);
            else if (i > full)         ip[i] = (byte) 0xFF;
        }
        return ip;
    }

    private void validateMask(int mask) {
        if (mask < 0 || mask > 32)
            throw new IllegalArgumentException("Mask must be between 0 and 32");
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ── Redis ─────────────────────────────────────────────────────────────────

    private void saveScanResult(String taskId, List<ScanResultEntry> results) {
        try {
            String json = objectMapper.writeValueAsString(results);
            redisTemplate.opsForValue().set(taskKey(taskId), json, resultTtl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize scan result for task " + taskId, e);
        }
    }

    private List<ScanResultEntry> getScanResult(String taskId) {
        String json = redisTemplate.opsForValue().get(taskKey(taskId));
        if (json == null) return null;
        try {
            return objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ScanResultEntry.class)
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize scan result for task {}", taskId, e);
            return null;
        }
    }

    private String taskKey(String taskId) {
        return redisKeyPrefix + ":task:" + taskId;
    }
}
