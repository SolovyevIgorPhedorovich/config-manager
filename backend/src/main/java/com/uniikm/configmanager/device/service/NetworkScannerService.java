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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final Executor taskExecutor;

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
                                 NetworkProbeService networkProbeService,
                                 @Qualifier("taskExecutor") Executor taskExecutor) {
        this.deviceRepo = deviceRepo;
        this.deviceGroupRepo = deviceGroupRepo;
        this.deviceOSRepo = deviceOSRepo;
        this.deviceMapper = deviceMapper;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.networkProbeService = networkProbeService;
        this.taskExecutor = taskExecutor;
    }

    // v1/v2c + SSH/WinRM (без SNMPv3) — используется планировщиком сканирования
    public ResponseEntity<Map<String, Object>> startScan(
            String ipaddr, int mask, int port,
            String community, String snmpv, String scanMode,
            String sshUsername, String sshPassword,
            String winrmUsername, String winrmPassword) {
        return startScan(ipaddr, mask, port, community, snmpv, scanMode,
                null, null, null, null, null,
                sshUsername, sshPassword, winrmUsername, winrmPassword);
    }

    public ResponseEntity<Map<String, Object>> startScan(
            String ipaddr, int mask, int port,
            String community, String snmpv, String scanMode,
            String securityName, String authProtocol, String authPassword,
            String privProtocol, String privPassword,
            String sshUsername, String sshPassword,
            String winrmUsername, String winrmPassword) {

        if (mask > 24) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Максимальная маска для сканирования: /24 (" + maxHosts + " хостов)"
            ));
        }

        ScanConfig config = new ScanConfig(
                port, community, snmpv,
                securityName, authProtocol, authPassword, privProtocol, privPassword,
                sshUsername, sshPassword,
                winrmUsername, winrmPassword,
                pingEnabled
        );
        String taskId = UUID.randomUUID().toString();

        // Запускаем скан в фоне через executor (а НЕ self-invocation @Async,
        // который не работает при вызове метода из того же класса и блокировал HTTP-поток).
        taskExecutor.execute(() -> {
            try {
                List<ScanResultEntry> results = scanAsync(taskId, ipaddr, mask, scanMode, config).join();
                saveScanResult(taskId, results);
            } catch (Exception ex) {
                log.error("Scan failed for taskId {}: {}", taskId, ex.getMessage(), ex);
                saveScanResult(taskId, List.of());
            }
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
            int[] prog = readProgress(taskId);
            int scanned = prog != null ? prog[0] : 0;
            int total   = prog != null ? prog[1] : 0;
            int percent = total > 0 ? (int) (scanned * 100L / total) : 0;
            Map<String, Object> running = new LinkedHashMap<>();
            running.put("status", "running");
            running.put("scanned", scanned);
            running.put("total", total);
            running.put("percent", percent);
            running.put("message", "Сканирование: " + scanned + "/" + total);
            return ResponseEntity.ok(running);
        }
        redisTemplate.delete(taskKey(taskId));
        redisTemplate.delete(progressKey(taskId));

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

    /**
     * Инвентаризация одного устройства: опрашиваем его по сети (SNMP/WinRM/SSH),
     * обновляем имя и ОС (sysDescr) в БД и возвращаем собранные данные.
     */
    @Transactional
    public Map<String, Object> inventoryDevice(Long deviceId, ScanConfig config) {
        DeviceInfo device = deviceRepo.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Устройство не найдено: " + deviceId));

        String ip = device.getIps().isEmpty() ? device.getHostname() : device.getIps().get(0).getIp();
        log.info("Инвентаризация устройства {} ({})", device.getHostname(), ip);

        DeviceProbeResult probe = networkProbeService.probe(ip, config);
        if (probe == null) {
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("success", false);
            failure.put("ip", ip);
            failure.put("message", "Устройство не ответило на опрос (" + ip + ")");
            return failure;
        }

        boolean changed = updateDeviceFromProbe(device, probe);
        if (changed) deviceRepo.save(device);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("updated", changed);
        result.put("detectionMethod", probe.detectionMethod());
        result.put("sysDescr", probe.sysDescr());
        result.put("device", deviceMapper.toResponse(device));
        return result;
    }

    @Async("taskExecutor")
    public CompletableFuture<List<ScanResultEntry>> scanAsync(
            String taskId, String ipStart, int mask, String scanMode, ScanConfig config) {
        try {
            validateMask(mask);
            int hostsCount = (int) Math.pow(2, 32 - mask);
            if (hostsCount > maxHosts) {
                throw new IllegalArgumentException("Слишком большая сеть! Максимум хостов: " + maxHosts);
            }

            log.info("=== Старт сканирования: {}/{}, режим='{}', хостов={}, ping={}, ssh={}, winrm={} ===",
                    ipStart, mask, scanMode, hostsCount, config.pingEnabled(),
                    config.hasSsh() ? "есть" : "нет", config.hasWinRM() ? "есть" : "нет");

            InetAddress startIp = InetAddress.getByName(ipStart);
            byte[] ipBytes = startIp.getAddress();

            // Список адресов для опроса (без network/broadcast) — нужен, чтобы знать total для прогресса
            List<String> ipsToScan = new ArrayList<>();
            for (int i = 0; i < hostsCount; i++) {
                String ipStr = incrementIp(ipBytes, i);
                if (!isNetworkOrBroadcast(ipStr, mask)) ipsToScan.add(ipStr);
            }
            final int total = ipsToScan.size();
            final AtomicInteger done = new AtomicInteger(0);
            saveProgress(taskId, 0, total);

            ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
            List<CompletableFuture<DeviceProbeResult>> probeFutures = new ArrayList<>();

            for (String ipStr : ipsToScan) {
                probeFutures.add(CompletableFuture.supplyAsync(() -> {
                    DeviceProbeResult r = networkProbeService.probe(ipStr, config);
                    saveProgress(taskId, done.incrementAndGet(), total);
                    return r;
                }, executor));
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
                    log.info("Хост {}: метод={}, тип(code)={}, hostname='{}', sysDescr='{}'",
                            probe.ip(), probe.detectionMethod(), typeCode, probe.hostname(),
                            truncate(probe.sysDescr(), 120));

                    if (!matchesScanMode(typeCode, scanMode)) {
                        log.info("Хост {}: ПРОПУЩЕН — тип(code={}) не подходит под режим '{}'",
                                probe.ip(), typeCode, scanMode);
                        continue;
                    }

                    Optional<DeviceInfo> existing = deviceRepo.findByIp(probe.ip());
                    if (existing.isPresent()) {
                        DeviceInfo device = existing.get();
                        boolean changed = updateDeviceFromProbe(device, probe);
                        if (changed) deviceRepo.save(device);
                        DeviceResponse response = deviceMapper.toResponse(device);
                        log.info("Хост {}: {} (id={})", probe.ip(), changed ? "ОБНОВЛЁН" : "БЕЗ ИЗМЕНЕНИЙ", device.getId());
                        results.add(new ScanResultEntry(response, changed ? "UPDATED" : "EXISTING"));
                    } else {
                        DeviceInfo newDevice = createDeviceFromProbe(probe, typeCode);
                        DeviceInfo saved = deviceRepo.save(newDevice);
                        DeviceResponse response = deviceMapper.toResponse(saved);
                        log.info("Хост {}: ДОБАВЛЕН как новый (id={})", probe.ip(), saved.getId());
                        results.add(new ScanResultEntry(response, "NEW"));
                    }
                } catch (Exception e) {
                    log.warn("Ошибка обработки {} в БД: {}", probe.ip(), e.getMessage(), e);
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
        if (sysDescr == null) return DeviceType.PC.getCode();
        String lower = sysDescr.toLowerCase();
        if (lower.contains("windows"))
            return DeviceType.PC.getCode();
        if (lower.contains("linux") || lower.contains("ubuntu") || lower.contains("debian")
                || lower.contains("centos") || lower.contains("redhat") || lower.contains("fedora"))
            return DeviceType.PC.getCode();
        if (lower.contains("canon") || lower.contains("kyocera") || lower.contains("xerox")
                || lower.contains("ricoh") || lower.contains("brother") || lower.contains("epson")
                || (lower.contains("hp") && lower.contains("laserjet")))
            return DeviceType.МФУ.getCode();
        if (lower.contains("cisco") || lower.contains("ios software"))
            return DeviceType.CISCO.getCode();
        if (lower.contains("proxmox") || lower.contains("vmware") || lower.contains("esxi"))
            return DeviceType.PROXMOX.getCode();
        return DeviceType.PC.getCode();
    }

    private boolean matchesScanMode(int typeCode, String scanMode) {
        if (scanMode == null || "all".equalsIgnoreCase(scanMode)) return true;
        return switch (scanMode.toLowerCase()) {
            case "windows", "pc", "linux" -> typeCode == DeviceType.PC.getCode();
            case "vm"    -> typeCode == DeviceType.PROXMOX.getCode();
            case "mfu"   -> typeCode == DeviceType.МФУ.getCode();
            case "cisco" -> typeCode == DeviceType.CISCO.getCode();
            default      -> true;
        };
    }

    // ── Domain: создание/обновление сущностей ─────────────────────────────────

    private boolean updateDeviceFromProbe(DeviceInfo device, DeviceProbeResult probe) {
        boolean changed = false;
        // Не понижаем реальное имя до голого IP (PORT-детект отдаёт hostname == ip)
        boolean hostnameIsJustIp = probe.hostname() != null && probe.hostname().equals(probe.ip());
        if (!hostnameIsJustIp && !probe.hostname().equals(device.getHostname())) {
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
        for (int i = 0; i < 4; i++) {
            int netBits = mask - i * 8;            // сколько сетевых бит приходится на этот октет
            if (netBits >= 8) continue;            // октет полностью сетевой — не трогаем
            if (netBits <= 0) ip[i] = 0;           // октет полностью хостовый → 0
            else ip[i] &= (byte) (0xFF << (8 - netBits)); // частичный → оставляем старшие netBits
        }
        return ip;
    }

    private byte[] getBroadcastAddressBytes(byte[] ip, int mask) {
        for (int i = 0; i < 4; i++) {
            int netBits = mask - i * 8;
            if (netBits >= 8) continue;            // октет полностью сетевой — не трогаем
            if (netBits <= 0) ip[i] = (byte) 0xFF; // октет полностью хостовый → 255
            else ip[i] |= (byte) (0xFF >> netBits); // частичный → младшие (8-netBits) бит в 1
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

    // ── Прогресс сканирования (done/total) ─────────────────────────────────────

    private String progressKey(String taskId) {
        return redisKeyPrefix + ":progress:" + taskId;
    }

    private void saveProgress(String taskId, int done, int total) {
        try {
            redisTemplate.opsForValue().set(progressKey(taskId), done + "/" + total, resultTtl);
        } catch (Exception e) {
            log.debug("Не удалось сохранить прогресс {}: {}", taskId, e.getMessage());
        }
    }

    /** Возвращает [done, total] или null, если прогресс ещё не записан. */
    private int[] readProgress(String taskId) {
        String v = redisTemplate.opsForValue().get(progressKey(taskId));
        if (v == null || !v.contains("/")) return null;
        try {
            String[] p = v.split("/", 2);
            return new int[]{ Integer.parseInt(p[0]), Integer.parseInt(p[1]) };
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
