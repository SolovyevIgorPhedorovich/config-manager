// NetworkScannerService.java — полная рабочая версия

package com.project.configmanager.service;

import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.project.configmanager.model.device.DeviceInfo;
import com.project.configmanager.model.device.DeviceOS;
import com.project.configmanager.model.device.DeviceGroup;
import com.project.configmanager.model.device.DeviceIP;
import com.project.configmanager.repository.DeviceRepository;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class NetworkScannerService {

    private final DeviceRepository deviceRepo;

    @Value("${scanner.max-parallel:16}")
    private int maxParallel = 16;

    public NetworkScannerService(DeviceRepository deviceRepo) {
        this.deviceRepo = deviceRepo;
    }

    @Async("taskExecutor")
    public CompletableFuture<List<DeviceInfo>> scanAsync(String ipStart, int mask, int port, String community, String snmpVersion) {
        List<DeviceInfo> foundDevices = new ArrayList<>();
        
        try {
            if (mask < 0 || mask > 32) throw new IllegalArgumentException("Маска должна быть от 0 до 32");
            
            InetAddress startIp = InetAddress.getByName(ipStart);
            byte[] ipBytes = startIp.getAddress();
            int hostsCount = (int) Math.pow(2, 32 - mask);

            if (hostsCount > 10_000) {
                throw new IllegalArgumentException("Слишком большая сеть! Максимум: /24 (256 хостов)");
            }

            ExecutorService executor = Executors.newFixedThreadPool(maxParallel);
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (int i = 0; i < hostsCount; i++) {
                byte[] currentIp = ipBytes.clone();
                
                // Увеличиваем IP на i
                int carry = i;
                for (int j = 3; j >= 0; j--) {
                    int sum = (currentIp[j] & 0xFF) + carry;
                    currentIp[j] = (byte) (sum % 256);
                    carry = sum / 256;
                    if (carry == 0) break;
                }

                String ipStr = ((currentIp[0] & 0xFF) + "." +
                                (currentIp[1] & 0xFF) + "." +
                                (currentIp[2] & 0xFF) + "." +
                                (currentIp[3] & 0xFF));

                // Пропускаем сетевой и broadcast адреса
                if (mask > 0 && mask < 32) {
                    try {
                        InetAddress addr = InetAddress.getByName(ipStr);
                        byte[] netAddr = getNetworkAddressBytes(addr, mask);
                        byte[] brdAddr = getBroadcastAddressBytes(addr, mask);

                        if (InetAddress.getByAddress(netAddr).equals(addr)) continue; 
                        if (InetAddress.getByAddress(brdAddr).equals(addr)) continue;
                    } catch (Exception e) {
                    }
                }

                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        DeviceInfo device = probeDevice(ipStr, port, community, snmpVersion);
                        if (device != null && !deviceRepo.existsByIp(device.getIps().get(0).getIp())) {
                            synchronized (foundDevices) {
                                foundDevices.add(device);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Ошибка при сканировании " + ipStr + ": " + e.getMessage());
                    }
                }, executor));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            executor.shutdown();

            int savedCount = deviceRepo.saveAll(foundDevices).size();
            System.out.printf("Найдено %d устройств, сохранено %d новых.%n", foundDevices.size(), savedCount);

        } catch (Exception e) {
            throw new RuntimeException("Ошибка сканирования: " + e.getMessage(), e);
        }

        return CompletableFuture.completedFuture(foundDevices);
    }

    // --- Методы для SNMP-запросов ---

    private DeviceInfo probeDevice(String ip, int port, String community, String version) throws Exception {
        TransportMapping<UdpAddress> transport = new DefaultUdpTransportMapping();
        Snmp snmp = null;

        try {
            snmp = new Snmp(transport);
            transport.listen();

            Address targetAddr = GenericAddress.parse("udp:" + ip + "/" + port);
            CommunityTarget<Address> target = new CommunityTarget<>();
            target.setCommunity(new OctetString(community));
            target.setAddress(targetAddr);
            target.setRetries(1);
            target.setTimeout(500);

            switch (version) {
                case "v1": target.setVersion(SnmpConstants.version1); break;
                case "v2c": target.setVersion(SnmpConstants.version2c); break;
                case "v3": target.setVersion(SnmpConstants.version3); break;
                default: throw new IllegalArgumentException("Поддерживаемые версии SNMP: v1, v2c, v3");
            }

            PDU pdu = new PDU();
            pdu.add(new VariableBinding(SnmpConstants.sysName));
            pdu.add(new VariableBinding(SnmpConstants.sysDescr));
            pdu.setType(PDU.GET);

            ResponseEvent<Address> response = snmp.send(pdu, target);

            if (response != null && response.getResponse() != null) {
                PDU respPDU = response.getResponse();
                String hostname = getStr(respPDU, SnmpConstants.sysName);
                String osVersion = getStr(respPDU, SnmpConstants.sysDescr);

                if (hostname == null || hostname.trim().isEmpty()) return null;

                DeviceInfo device = new DeviceInfo();
                DeviceIP deviceIP = new DeviceIP();
                DeviceOS deviceOS = new DeviceOS();
                DeviceGroup deviceGroup = new DeviceGroup();
                deviceGroup.setName("");
                deviceIP.setDevice(device);
                deviceIP.setIp(ip);
                deviceOS.setName(osVersion);
                device.setHostname(hostname.trim());
                device.setIp(deviceIP);
                device.setTypeCode(detectDeviceType(osVersion));
                device.setGroup(deviceGroup);
                device.setOsVersion(deviceOS);
                device.setIsActive(true);

                return device;
            }
        } finally {
            if (snmp != null) {
                try { snmp.close(); } catch (Exception ignored) {}
            }
            try { transport.close(); } catch (Exception ignored) {}
        }

        return null;
    }

    private String getStr(PDU pdu, OID oid) {
        if (pdu == null || pdu.size() == 0) return null;

        for (VariableBinding vb : pdu.getVariableBindings()) {
        if (vb != null && oid.equals(vb.getOid())) {
            Variable var = vb.getVariable();
            return var == null ? null : var.toString();
        }
    }
        return null;
    }

    private int detectDeviceType(String osVersion) {
        if (osVersion == null) return 6;

        String lower = osVersion.toLowerCase();

        if (lower.contains("windows")) return 0;
        if (lower.contains("linux") || lower.contains("alt linux")) return 1;
        if (lower.contains("canon")) return 2;
        if (lower.contains("kyocera")) return 3;
        if (lower.contains("cisco") && lower.contains("switch")) return 4;
        if (lower.contains("cisco") && lower.contains("router")) return 5;

        return 6; // Proxmox/VM по умолчанию
    }

    // --- Вспомогательные методы IP ---

    private byte[] getNetworkAddressBytes(InetAddress addr, int mask) {
        try {
            byte[] ip = addr.getAddress();
            if (mask == 0) return new byte[]{0, 0, 0, 0};

            int fullOctets = mask / 8;
            int bitsInLastOctet = mask % 8;

            for (int i = 0; i < 4; i++) {
                if (i < fullOctets) continue;
                byte maskByte = (byte) (0xff << (8 - bitsInLastOctet));
                ip[i] &= maskByte;
                break;
            }
            return ip;
        } catch (Exception e) {
            throw new RuntimeException("Ошибка расчета сетевого адреса", e);
        }
    }

    private byte[] getBroadcastAddressBytes(InetAddress addr, int mask) {
        try {
            byte[] ip = addr.getAddress();
            if (mask == 32) return ip;
            if (mask == 0) return new byte[]{(byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff};

            int fullOctets = mask / 8;
            int bitsInLastOctet = mask % 8;

            for (int i = 0; i < 4; i++) {
                if (i < fullOctets) continue;
                byte maskByte = (byte) (~((0xff << (8 - bitsInLastOctet)) & 0xFF));
                ip[i] |= maskByte;
                break;
            }
            return ip;
        } catch (Exception e) {
            throw new RuntimeException("Ошибка расчета broadcast-адреса", e);
        }
    }

}
