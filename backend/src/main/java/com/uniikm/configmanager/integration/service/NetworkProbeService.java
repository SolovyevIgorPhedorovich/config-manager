package com.uniikm.configmanager.integration.service;

import com.uniikm.configmanager.common.dto.ConnectionProtocol;
import com.uniikm.configmanager.common.dto.DeviceCommandTarget;
import com.uniikm.configmanager.integration.adater.SSHAdapter;
import com.uniikm.configmanager.integration.adater.WinRMAdapter;
import com.uniikm.configmanager.integration.dto.DeviceProbeResult;
import com.uniikm.configmanager.integration.dto.ScanConfig;

import lombok.extern.slf4j.Slf4j;
import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class NetworkProbeService {

    @Value("${network-scan.ping-timeout:2000}")
    private int pingTimeoutMs;

    @Value("${network-scan.port-scan-timeout:1000}")
    private int portScanTimeoutMs;

    @Value("${network-scan.ssh-connect-timeout:5000}")
    private int sshConnectTimeoutMs;

    @Value("${network-scan.winrm-connect-timeout:8000}")
    private int winrmConnectTimeoutMs;

    private static final OID OID_SYS_LOCATION = new OID("1.3.6.1.2.1.1.6.0");
    private static final OID OID_SYS_CONTACT  = new OID("1.3.6.1.2.1.1.4.0");
    private static final OID OID_SYS_OBJ_ID   = new OID("1.3.6.1.2.1.1.2.0");

    private static final Pattern CISCO_MODEL_PATTERN = Pattern.compile(
        "(?im)^.*\\bcisco\\s+(?!IOS\\b|Systems\\b|Internetwork\\b)([A-Z0-9][A-Z0-9/._-]{2,})"
    );
    private static final Pattern CISCO_MODEL_NUMBER_PATTERN = Pattern.compile(
        "(?im)Model(?:\\s+Number)?\\s*[:\\s]\\s*([A-Z0-9][A-Z0-9/._-]+)"
    );

    public int getPingTimeoutMs() {
        return pingTimeoutMs;
    }

    /**
     * Опрашивает хост всеми доступными методами: SNMP → WinRM → SSH → PORT.
     * Возвращает null если хост недоступен или не отвечает ни по одному протоколу.
     */
    public DeviceProbeResult probe(String ip, ScanConfig config) {
        if (config.pingEnabled() && !isHostReachable(ip)) {
            log.debug("Host {} unreachable, skipping", ip);
            return null;
        }
        return probeHost(ip, config);
    }

    // ── Protocol probes ───────────────────────────────────────────────────────

    private DeviceProbeResult probeHost(String ip, ScanConfig config) {
        DeviceProbeResult snmpResult = probeViaSNMP(ip, config.snmpPort(), config.community(), config.snmpVersion());
        if (snmpResult != null) return snmpResult;

        boolean winrmHttp  = isPortOpen(ip, 5985);
        boolean winrmHttps = isPortOpen(ip, 5986);
        boolean sshOpen    = isPortOpen(ip, 22);

        if ((winrmHttp || winrmHttps) && config.hasWinRM()) {
            DeviceProbeResult result = probeViaWinRM(ip, config.winrmUsername(), config.winrmPassword());
            if (result != null) return result;
        }

        if (sshOpen && config.hasSsh()) {
            DeviceProbeResult result = probeViaSsh(ip, config.sshUsername(), config.sshPassword());
            if (result != null) return result;
        }

        if (winrmHttp || winrmHttps) {
            return new DeviceProbeResult(ip, ip, "Windows (WinRM port open)", null, null, "Microsoft", null, "PORT");
        }
        if (sshOpen) {
            return new DeviceProbeResult(ip, ip, "Linux (SSH port open)", null, null, null, null, "PORT");
        }

        return null;
    }

    private DeviceProbeResult probeViaSNMP(String ip, int port, String community, String version) {
        // Намеренно создаём локальный Snmp-экземпляр, а не используем singleton SnmpClient:
        // сканирование параллельно на сотнях хостов, singleton не thread-safe для concurrent connect().
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
            target.setVersion(resolveSnmpVersion(version));

            PDU pdu = new PDU();
            pdu.add(new VariableBinding(SnmpConstants.sysName));
            pdu.add(new VariableBinding(SnmpConstants.sysDescr));
            pdu.add(new VariableBinding(OID_SYS_LOCATION));
            pdu.add(new VariableBinding(OID_SYS_CONTACT));
            pdu.add(new VariableBinding(OID_SYS_OBJ_ID));
            pdu.setType(PDU.GET);

            ResponseEvent<Address> response = snmp.send(pdu, target);
            if (response == null || response.getResponse() == null) return null;

            PDU respPDU = response.getResponse();
            String hostname = getStringValue(respPDU, SnmpConstants.sysName);
            if (hostname == null || hostname.isBlank()) return null;

            String sysDescr    = getStringValue(respPDU, SnmpConstants.sysDescr);
            String sysLocation = getStringValue(respPDU, OID_SYS_LOCATION);
            String sysContact  = getStringValue(respPDU, OID_SYS_CONTACT);

            return new DeviceProbeResult(
                    ip, hostname.trim(),
                    sysDescr != null ? sysDescr.trim() : "Unknown",
                    sysLocation, sysContact,
                    extractVendor(sysDescr),
                    extractModelFromSysDescr(sysDescr),
                    "SNMP"
            );
        } catch (Exception e) {
            log.debug("SNMP probe failed for {}: {}", ip, e.getMessage());
            return null;
        } finally {
            if (snmp != null)      try { snmp.close();      } catch (Exception ignored) {}
            if (transport != null) try { transport.close(); } catch (Exception ignored) {}
        }
    }

    private DeviceProbeResult probeViaWinRM(String ip, String username, String password) {
        int[] ports = {5985, 5986};
        for (int winrmPort : ports) {
            if (!isPortOpen(ip, winrmPort)) continue;

            WinRMAdapter winrm = new WinRMAdapter(ip, winrmPort, username, password);
            DeviceCommandTarget target = new DeviceCommandTarget(
                    ip, winrmPort, username, password, ConnectionProtocol.WINRM,
                    null, winrmPort == 5986, winrmPort == 5986);
            try {
                Map<String, Object> connResult = winrm.connect(target).get(winrmConnectTimeoutMs, TimeUnit.MILLISECONDS);
                if (!Boolean.TRUE.equals(connResult.get("success"))) continue;

                String cmd = "$os = Get-CimInstance Win32_OperatingSystem; " +
                             "$cs = Get-CimInstance Win32_ComputerSystem; " +
                             "Write-Output \"$($cs.DNSHostName)|$($os.Caption)|$($os.Version)|$($cs.Manufacturer)|$($cs.Model)\"";

                Map<String, Object> result = winrm.executeCommand(cmd).get(15000, TimeUnit.MILLISECONDS);
                String stdout = (String) result.get("stdout");

                if (stdout != null && !stdout.isBlank()) {
                    String[] parts = stdout.trim().split("\\|", -1);
                    String hostname     = parts.length > 0 && !parts[0].isBlank() ? parts[0].trim() : ip;
                    String caption      = parts.length > 1 ? parts[1].trim() : "Windows";
                    String version      = parts.length > 2 ? parts[2].trim() : "";
                    String manufacturer = parts.length > 3 && !parts[3].isBlank() ? parts[3].trim() : "Microsoft";
                    String model        = parts.length > 4 && !parts[4].isBlank() ? parts[4].trim() : null;
                    String sysDescr     = caption + (version.isEmpty() ? "" : " " + version);
                    return new DeviceProbeResult(ip, hostname, sysDescr, null, null, manufacturer, model, "WINRM");
                }

            } catch (TimeoutException e) {
                log.debug("WinRM probe timed out for {} port {}", ip, winrmPort);
            } catch (Exception e) {
                log.debug("WinRM probe failed for {} port {}: {}", ip, winrmPort, e.getMessage());
            } finally {
                try { winrm.disconnect().get(2000, TimeUnit.MILLISECONDS); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private DeviceProbeResult probeViaSsh(String ip, String username, String password) {
        SSHAdapter ssh = new SSHAdapter(ip, 22, username, password);
        DeviceCommandTarget target = new DeviceCommandTarget(
                ip, 22, username, password, ConnectionProtocol.SSH, null, false, false);
        try {
            Map<String, Object> connResult = ssh.connect(target).get(sshConnectTimeoutMs, TimeUnit.MILLISECONDS);
            if (!Boolean.TRUE.equals(connResult.get("success"))) {
                log.debug("SSH connect failed for {}: {}", ip, connResult.get("error"));
                return null;
            }

            String hostname = execSsh(ssh, "hostname 2>/dev/null", 5000);
            String unameA   = execSsh(ssh, "uname -a 2>/dev/null", 5000);

            if (unameA != null && unameA.toLowerCase().contains("linux")) {
                return buildLinuxProbeData(ip, hostname, unameA, ssh);
            }

            return buildCiscoProbeData(ip, ssh);

        } catch (TimeoutException e) {
            log.debug("SSH probe timed out for {}", ip);
        } catch (Exception e) {
            log.debug("SSH probe failed for {}: {}", ip, e.getMessage());
        } finally {
            try { ssh.disconnect().get(2000, TimeUnit.MILLISECONDS); } catch (Exception ignored) {}
        }
        return null;
    }

    private DeviceProbeResult buildLinuxProbeData(String ip, String hostname, String unameA, SSHAdapter ssh) {
        String osRelease = execSsh(ssh,
            "cat /etc/os-release 2>/dev/null | grep PRETTY_NAME | cut -d= -f2 | tr -d '\"' | head -1", 5000);

        boolean isProxmox = unameA.contains("-pve") ||
                            (osRelease != null && osRelease.toLowerCase().contains("proxmox"));
        String sysDescr;
        if (isProxmox) {
            String pveVer = execSsh(ssh, "pveversion 2>/dev/null | head -1", 5000);
            sysDescr = (pveVer != null && !pveVer.isBlank())
                    ? "Proxmox VE " + pveVer.trim()
                    : (osRelease != null && !osRelease.isBlank() ? osRelease.trim() : "Proxmox VE " + unameA);
        } else {
            sysDescr = (osRelease != null && !osRelease.isBlank()) ? osRelease.trim() : unameA;
        }

        String resolvedHost = (hostname != null && !hostname.isBlank()) ? hostname.trim() : ip;
        return new DeviceProbeResult(ip, resolvedHost, sysDescr, null, null, extractVendor(sysDescr), null, "SSH");
    }

    private DeviceProbeResult buildCiscoProbeData(String ip, SSHAdapter ssh) {
        String showVer = execSsh(ssh, "show version", 10000);
        if (showVer == null) return null;

        String lower = showVer.toLowerCase();
        if (!lower.contains("cisco") && !lower.contains("ios software")) return null;

        String ciscoHostname = fetchCiscoHostname(ssh, ip);
        String model         = extractCiscoModelFromShowVersion(showVer);
        return new DeviceProbeResult(ip, ciscoHostname, truncate(showVer, 250), null, null, "Cisco", model, "SSH");
    }

    private String fetchCiscoHostname(SSHAdapter ssh, String fallback) {
        String output = execSsh(ssh, "show running-config | include ^hostname", 10000);
        if (output != null && output.toLowerCase().startsWith("hostname ")) {
            return output.substring("hostname ".length()).trim();
        }
        return fallback;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String execSsh(SSHAdapter ssh, String command, int timeoutMs) {
        try {
            Map<String, Object> result = ssh.executeCommand(command).get(timeoutMs, TimeUnit.MILLISECONDS);
            Object stdout = result.get("stdout");
            return stdout != null ? stdout.toString().trim() : null;
        } catch (Exception e) {
            log.debug("SSH exec '{}' failed: {}", command, e.getMessage());
            return null;
        }
    }

    private boolean isPortOpen(String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), portScanTimeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isHostReachable(String ip) {
        try {
            InetAddress inet = InetAddress.getByName(ip);
            return inet.isReachable(pingTimeoutMs);
        } catch (Exception e) {
            return false;
        }
    }

    public String extractVendor(String sysDescr) {
        if (sysDescr == null) return null;
        String lower = sysDescr.toLowerCase();
        if (lower.contains("cisco"))     return "Cisco";
        if (lower.contains("canon"))     return "Canon";
        if (lower.contains("kyocera"))   return "Kyocera";
        if (lower.contains("hp"))        return "HP";
        if (lower.contains("xerox"))     return "Xerox";
        if (lower.contains("ricoh"))     return "Ricoh";
        if (lower.contains("brother"))   return "Brother";
        if (lower.contains("epson"))     return "Epson";
        if (lower.contains("samsung"))   return "Samsung";
        if (lower.contains("microsoft")) return "Microsoft";
        if (lower.contains("vmware"))    return "VMware";
        if (lower.contains("proxmox"))   return "Proxmox";
        return null;
    }

    private String extractModelFromSysDescr(String sysDescr) {
        if (sysDescr == null || !sysDescr.toLowerCase().contains("cisco ios")) return null;
        int start = sysDescr.toLowerCase().indexOf("cisco ios");
        int end   = sysDescr.indexOf(",", start);
        return end > start ? sysDescr.substring(start, end).trim() : null;
    }

    private String extractCiscoModelFromShowVersion(String showVersion) {
        if (showVersion == null) return null;
        Matcher m = CISCO_MODEL_PATTERN.matcher(showVersion);
        if (m.find()) return m.group(1);
        Matcher m2 = CISCO_MODEL_NUMBER_PATTERN.matcher(showVersion);
        if (m2.find()) return m2.group(1);
        return null;
    }

    private int resolveSnmpVersion(String version) {
        return switch (version.toLowerCase()) {
            case "v1"  -> SnmpConstants.version1;
            case "v3"  -> SnmpConstants.version3;
            default    -> SnmpConstants.version2c;
        };
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

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
