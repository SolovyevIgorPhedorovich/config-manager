package com.uniikm.configmanager.device.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniikm.configmanager.audit.enums.AuditAction;
import com.uniikm.configmanager.audit.model.EventLogEntity;
import com.uniikm.configmanager.audit.repository.AuditLogRepository;
import com.uniikm.configmanager.device.model.DeviceGroup;
import com.uniikm.configmanager.device.model.DeviceIP;
import com.uniikm.configmanager.device.model.DeviceInfo;
import com.uniikm.configmanager.device.model.DeviceOS;
import com.uniikm.configmanager.device.repository.DeviceRepository;

import lombok.extern.slf4j.Slf4j;
import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class NetworkScannerService {

    private final DeviceRepository deviceRepo;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    @Value("${network-scan.redis-key-prefix:network-scan}")
    private String redisKeyPrefix;

    @Value("${network-scan.result-ttl:PT5M}")
    private Duration resultTtl;

    @Value("${network-scan.max-hosts:256}")
    private int maxHosts;

    @Value("${network-scan.thread-pool-size:20}")
    private int threadPoolSize;

    @Value("${network-scan.ping-timeout:2000}")
    private int pingTimeoutMs;
    
    @Value("${network-scan.ping-enabled:true}")
    private boolean pingEnabled;

    public NetworkScannerService(DeviceRepository deviceRepo,
                                 AuditLogRepository auditLogRepository,
                                 ObjectMapper objectMapper,
                                 StringRedisTemplate redisTemplate) {
        this.deviceRepo = deviceRepo;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    public ResponseEntity<Map<String, Object>> startScan(String ipaddr, int mask, int port,
                                                         String community, String snmpv) {
        if (mask > 24) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Максимальная маска для сканирования: /24 (" + maxHosts + " хостов)"
            ));
        }

        String taskId = UUID.randomUUID().toString();

        CompletableFuture<List<DeviceInfo>> future = scanAsync(ipaddr, mask, port, community, snmpv);
        future.thenAccept(devices -> saveScanResult(taskId, devices))
              .exceptionally(ex -> {
                  log.error("Scan failed for taskId {}: {}", taskId, ex.getMessage());
                  saveScanResult(taskId, List.of());
                  return null;
              });

        return ResponseEntity.ok(Map.of(
                "taskId", taskId,
                "status", "started",
                "message", "Сканирование запущено. Используйте /api/devices/scan/status?taskId=" + taskId
        ));
    }

    public ResponseEntity<Object> getResult(String taskId) {
        List<DeviceInfo> devices = getScanResult(taskId);
        if (devices == null) {
            return ResponseEntity.ok(Map.of(
                    "status", "running",
                    "message", "Сканирование ещё не завершено"
            ));
        }
        redisTemplate.delete(taskKey(taskId));
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "devices", devices,
                "count", devices.size()
        ));
    }

    @Async("taskExecutor")
    public CompletableFuture<List<DeviceInfo>> scanAsync(String ipStart, int mask, int port,
                                                         String community, String snmpVersion) {
        List<DeviceInfo> foundDevices = Collections.synchronizedList(new ArrayList<>());

        try {
            validateMask(mask);
            int hostsCount = (int) Math.pow(2, 32 - mask);
            if (hostsCount > maxHosts) {
                throw new IllegalArgumentException("Слишком большая сеть! Максимум хостов: " + maxHosts);
            }

            InetAddress startIp = InetAddress.getByName(ipStart);
            byte[] ipBytes = startIp.getAddress();

            ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < hostsCount; i++) {
                String ipStr = incrementIp(ipBytes, i);
                if (isNetworkOrBroadcast(ipStr, mask)) continue;

                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        // Проверка доступности хоста через ping
                        if (pingEnabled && !isHostReachable(ipStr, pingTimeoutMs)) {
                            log.debug("Host {} is not reachable, skipping", ipStr);
                            return;
                        }
                        
                        DeviceInfo device = probeDevice(ipStr, port, community, snmpVersion);
                        if (device != null && !deviceRepo.existsByIp(device.getIps().get(0).getIp())) {
                            foundDevices.add(device);
                            log.debug("Found device: {} ({})", device.getHostname(), ipStr);
                        }
                    } catch (Exception e) {
                        log.debug("Scan error for {}: {}", ipStr, e.getMessage());
                    }
                }, executor));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            executor.shutdown();
            
            // Ждём завершения всех задач
            if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
                executor.shutdownNow();
            }

            if (!foundDevices.isEmpty()) {
                deviceRepo.saveAll(foundDevices);
                log.info("Scan finished. Found {} devices.", foundDevices.size());
            } else {
                log.info("Scan finished. No devices found.");
            }

        } catch (Exception e) {
            log.error("Scan failed", e);
            throw new RuntimeException("Ошибка сканирования: " + e.getMessage(), e);
        }

        return CompletableFuture.completedFuture(foundDevices);
    }

    /**
     * Проверка доступности хоста через стандартный Java ping
     */
    private boolean isHostReachable(String ip, int timeoutMs) {
        try {
            InetAddress inet = InetAddress.getByName(ip);
            
            // Используем Future для контроля таймаута
            ExecutorService pingExecutor = Executors.newSingleThreadExecutor();
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return inet.isReachable(timeoutMs);
                } catch (Exception e) {
                    log.trace("Ping error for {}: {}", ip, e.getMessage());
                    return false;
                }
            }, pingExecutor);
            
            try {
                return future.get(timeoutMs + 1000, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                log.debug("Ping timeout for {}", ip);
                future.cancel(true);
                return false;
            } finally {
                pingExecutor.shutdownNow();
            }
            
        } catch (Exception e) {
            log.trace("Cannot ping {}: {}", ip, e.getMessage());
            return false;
        }
    }

    /**
     * Пробует получить информацию об устройстве через SNMP
     */
    private DeviceInfo probeDevice(String ip, int port, String community, String version) {
        TransportMapping<UdpAddress> transport = null;
        Snmp snmp = null;
        
        try {
            transport = new DefaultUdpTransportMapping();
            snmp = new Snmp(transport);
            transport.listen();

            Address targetAddr = GenericAddress.parse("udp:" + ip + "/" + port);
            CommunityTarget<Address> target = new CommunityTarget<>();
            target.setCommunity(new OctetString(community));
            target.setAddress(targetAddr);
            target.setRetries(1);
            target.setTimeout(1500);

            switch (version.toLowerCase()) {
                case "v1" -> target.setVersion(SnmpConstants.version1);
                case "v2c" -> target.setVersion(SnmpConstants.version2c);
                case "v3" -> target.setVersion(SnmpConstants.version3);
                default -> throw new IllegalArgumentException("Unsupported SNMP version: " + version);
            }

            PDU pdu = new PDU();
            pdu.add(new VariableBinding(SnmpConstants.sysName));
            pdu.add(new VariableBinding(SnmpConstants.sysDescr));
            pdu.setType(PDU.GET);

            ResponseEvent<Address> response = snmp.send(pdu, target);
            if (response != null && response.getResponse() != null) {
                PDU respPDU = response.getResponse();
                String hostname = getStringValue(respPDU, SnmpConstants.sysName);
                String sysDescr = getStringValue(respPDU, SnmpConstants.sysDescr);

                if (hostname == null || hostname.trim().isEmpty()) {
                    return null;
                }

                DeviceInfo device = new DeviceInfo();
                DeviceIP deviceIP = new DeviceIP();
                deviceIP.setIp(ip);
                deviceIP.setDevice(device);
                device.setIps(List.of(deviceIP));

                DeviceOS deviceOS = new DeviceOS();
                deviceOS.setName(sysDescr != null ? sysDescr.trim() : "Unknown OS");
                device.setOsVersion(deviceOS);

                DeviceGroup group = new DeviceGroup();
                group.setName("Auto-discovered");
                device.setGroup(group);

                device.setHostname(hostname.trim());
                device.setTypeCode(detectDeviceType(sysDescr));
                device.setIsActive(true);

                return device;
            }
            
        } catch (Exception e) {
            log.debug("SNMP probe failed for {}: {}", ip, e.getMessage());
        } finally {
            if (snmp != null) {
                try { 
                    snmp.close(); 
                } catch (Exception ignored) {}
            }
            if (transport != null) {
                try { 
                    transport.close(); 
                } catch (Exception ignored) {}
            }
        }
        
        return null;
    }

    private String getStringValue(PDU pdu, OID oid) {
        if (pdu == null) return null;
        for (VariableBinding vb : pdu.getVariableBindings()) {
            if (vb != null && oid.equals(vb.getOid())) {
                Variable var = vb.getVariable();
                return var == null ? null : var.toString();
            }
        }
        return null;
    }

    private int detectDeviceType(String sysDescr) {
        if (sysDescr == null) return 6;
        String lower = sysDescr.toLowerCase();
        if (lower.contains("windows")) return 0;
        if (lower.contains("linux")) return 1;
        if (lower.contains("canon")) return 2;
        if (lower.contains("kyocera")) return 3;
        if (lower.contains("cisco") && lower.contains("switch")) return 4;
        if (lower.contains("cisco") && lower.contains("router")) return 5;
        return 6;
    }

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
            byte[] net = getNetworkAddressBytes(addr, mask);
            byte[] brd = getBroadcastAddressBytes(addr, mask);
            return Arrays.equals(net, addr.getAddress()) || Arrays.equals(brd, addr.getAddress());
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] getNetworkAddressBytes(InetAddress addr, int mask) {
        byte[] ip = addr.getAddress();
        int full = mask / 8;
        int bits = mask % 8;
        for (int i = full; i < 4; i++) {
            if (i == full && bits > 0) ip[i] &= (0xFF << (8 - bits));
            else if (i > full) ip[i] = 0;
        }
        return ip;
    }

    private byte[] getBroadcastAddressBytes(InetAddress addr, int mask) {
        byte[] ip = addr.getAddress();
        if (mask == 32) return ip;
        int full = mask / 8;
        int bits = mask % 8;
        for (int i = full; i < 4; i++) {
            if (i == full && bits > 0) ip[i] |= (0xFF >> bits);
            else if (i > full) ip[i] = (byte) 0xFF;
        }
        return ip;
    }

    private void validateMask(int mask) {
        if (mask < 0 || mask > 32) {
            throw new IllegalArgumentException("Mask must be between 0 and 32");
        }
    }

    private void saveScanResult(String taskId, List<DeviceInfo> devices) {
        try {
            String json = objectMapper.writeValueAsString(devices);
            redisTemplate.opsForValue().set(taskKey(taskId), json, resultTtl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize scan result for task " + taskId, e);
        }
    }

    private List<DeviceInfo> getScanResult(String taskId) {
        String json = redisTemplate.opsForValue().get(taskKey(taskId));
        if (json == null) return null;
        try {
            return objectMapper.readValue(
                json, 
                objectMapper.getTypeFactory().constructCollectionType(List.class, DeviceInfo.class)
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