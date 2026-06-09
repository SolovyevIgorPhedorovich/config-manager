package com.uniikm.configmanager.integration.service;

import com.uniikm.configmanager.common.dto.ConnectionProtocol;
import com.uniikm.configmanager.common.dto.DeviceCommandTarget;
import com.uniikm.configmanager.integration.adater.SSHAdapter;
import com.uniikm.configmanager.integration.adater.WinRMAdapter;
import com.uniikm.configmanager.integration.dto.DeviceProbeResult;
import com.uniikm.configmanager.integration.dto.ScanConfig;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.*;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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

    @Value("${ssh.connect-timeout-ms:30000}")
    private int sshSessionTimeoutMs;

    @Value("${ssh.disconnect-timeout-ms:2000}")
    private int sshDisconnectTimeoutMs;

    @Value("${winrm.default-port:5985}")
    private int winrmDefaultPort;

    @Value("${winrm.default-https-port:5986}")
    private int winrmDefaultHttpsPort;

    @Value("${winrm.command-timeout-ms:30000}")
    private long winrmCommandTimeoutMs;

    @Value("${winrm.disconnect-timeout-ms:2000}")
    private int winrmDisconnectTimeoutMs;

    @Value("${snmp.probe-timeout-ms:1500}")
    private long snmpProbeTimeoutMs;

    @Value("${snmp.probe-retries:1}")
    private int snmpProbeRetries;

    private static final OID OID_SYS_LOCATION = new OID("1.3.6.1.2.1.1.6.0");
    private static final OID OID_SYS_CONTACT  = new OID("1.3.6.1.2.1.1.4.0");
    private static final OID OID_SYS_OBJ_ID   = new OID("1.3.6.1.2.1.1.2.0");

    /** Регистрируем USM auth/priv протоколы (SHA, MD5, AES, DES) один раз при старте. */
    @PostConstruct
    void initSnmpSecurityProtocols() {
        SecurityProtocols.getInstance().addDefaultProtocols();
    }

    private static void addProbeBindings(PDU pdu) {
        pdu.add(new VariableBinding(SnmpConstants.sysName));
        pdu.add(new VariableBinding(SnmpConstants.sysDescr));
        pdu.add(new VariableBinding(OID_SYS_LOCATION));
        pdu.add(new VariableBinding(OID_SYS_CONTACT));
        pdu.add(new VariableBinding(OID_SYS_OBJ_ID));
        pdu.setType(PDU.GET);
    }

    private static final Pattern CISCO_MODEL_PATTERN = Pattern.compile(
        "(?im)^.*\\bcisco\\s+(?!IOS\\b|Systems\\b|Internetwork\\b)([A-Z0-9][A-Z0-9/._-]{2,})"
    );
    private static final Pattern CISCO_MODEL_NUMBER_PATTERN = Pattern.compile(
        "(?im)Model(?:\\s+Number)?\\s*[:\\s]\\s*([A-Z0-9][A-Z0-9/._-]+)"
    );

    public int getPingTimeoutMs() {
        return pingTimeoutMs;
    }

    /** Публичная проверка доступности хоста: системный ICMP-ping + TCP-фолбэк. */
    public boolean isReachable(String ip) {
        return isHostReachable(ip);
    }

    /**
     * Опрашивает хост всеми доступными методами: SNMP → WinRM → SSH → PORT.
     * Возвращает null если хост недоступен или не отвечает ни по одному протоколу.
     */
    public DeviceProbeResult probe(String ip, ScanConfig config) {
        if (config.pingEnabled() && !isHostReachable(ip)) {
            log.debug("[{}] недоступен (ICMP+TCP), пропуск", ip);
            return null;
        }
        DeviceProbeResult result = probeHost(ip, config);
        if (result == null) {
            log.debug("[{}] доступен, но не опознан ни по одному протоколу (SNMP/WinRM/SSH/порты)", ip);
        } else {
            log.debug("[{}] опознан: метод={}, sysDescr='{}'", ip, result.detectionMethod(), result.sysDescr());
        }
        return result;
    }

    // ── Protocol probes ───────────────────────────────────────────────────────

    private DeviceProbeResult probeHost(String ip, ScanConfig config) {
        DeviceProbeResult snmpResult = probeViaSNMP(ip, config);
        if (snmpResult != null) {
            log.debug("[{}] опознан по SNMP", ip);
            return snmpResult;
        }

        boolean winrmHttp  = isPortOpen(ip, 5985);
        boolean winrmHttps = isPortOpen(ip, 5986);
        boolean sshOpen    = isPortOpen(ip, 22);
        log.debug("[{}] SNMP нет; порты: winrm5985={}, winrm5986={}, ssh22={}", ip, winrmHttp, winrmHttps, sshOpen);

        if ((winrmHttp || winrmHttps) && config.hasWinRM()) {
            log.debug("[{}] порт WinRM открыт + есть креды → WinRM-опрос", ip);
            DeviceProbeResult result = probeViaWinRM(ip, config.winrmUsername(), config.winrmPassword());
            if (result != null) return result;
            log.debug("[{}] WinRM-опрос не дал результата", ip);
        }

        if (sshOpen && config.hasSsh()) {
            log.debug("[{}] порт 22 открыт + есть SSH-креды → SSH-опрос (user='{}')", ip, config.sshUsername());
            DeviceProbeResult result = probeViaSsh(ip, config.sshUsername(), config.sshPassword());
            if (result != null) return result;
            log.debug("[{}] SSH-опрос не дал результата", ip);
        } else if (sshOpen) {
            log.debug("[{}] порт 22 открыт, но SSH-креды не заданы → определю как Linux по порту", ip);
        }

        // Идентификация по открытым портам, когда SNMP/учётных данных нет.
        // Имя берём из обратного DNS (PTR), иначе — IP.
        if (winrmHttp || winrmHttps) {
            log.debug("[{}] → Windows (по открытому порту WinRM)", ip);
            return new DeviceProbeResult(ip, reverseHost(ip), "Windows (WinRM port open)", null, null, "Microsoft", null, "PORT");
        }
        boolean rdpOpen = isPortOpen(ip, 3389);
        boolean smbOpen = isPortOpen(ip, 445);
        if (rdpOpen || smbOpen) {
            log.debug("[{}] → Windows (по портам RDP={}, SMB={})", ip, rdpOpen, smbOpen);
            return new DeviceProbeResult(ip, reverseHost(ip), "Windows (RDP/SMB port open)", null, null, "Microsoft", null, "PORT");
        }
        if (sshOpen) {
            log.debug("[{}] → Linux (по открытому порту 22)", ip);
            return new DeviceProbeResult(ip, reverseHost(ip), "Linux (SSH port open)", null, null, null, null, "PORT");
        }

        return null;
    }

    private DeviceProbeResult probeViaSNMP(String ip, ScanConfig config) {
        // SNMPv3 использует USM (логин + auth/priv пароли), v1/v2c — community-строку.
        return config.isV3()
                ? probeViaSNMPv3(ip, config)
                : probeViaSNMPCommunity(ip, config.snmpPort(), config.community(), config.snmpVersion());
    }

    /** SNMP v1 / v2c — аутентификация через community-строку. */
    private DeviceProbeResult probeViaSNMPCommunity(String ip, int port, String community, String version) {
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
            target.setRetries(snmpProbeRetries);
            target.setTimeout(snmpProbeTimeoutMs);
            target.setVersion(resolveSnmpVersion(version));

            PDU pdu = new PDU();
            addProbeBindings(pdu);

            ResponseEvent<Address> response = snmp.send(pdu, target);
            return buildResult(ip, response);
        } catch (Exception e) {
            log.debug("SNMP probe failed for {}: {}", ip, e.getMessage());
            return null;
        } finally {
            if (snmp != null)      try { snmp.close();      } catch (Exception ignored) {}
            if (transport != null) try { transport.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * SNMP v3 — аутентификация через USM (логин + auth/priv пароли).
     * Уровень безопасности определяется наличием паролей:
     *   нет паролей            → noAuthNoPriv
     *   только auth-пароль      → authNoPriv
     *   auth + priv пароли      → authPriv
     * USM создаётся локально для каждого хоста (instance-local MPv3), чтобы не трогать
     * глобальный SecurityModels — иначе параллельное сканирование ломает друг друга.
     */
    private DeviceProbeResult probeViaSNMPv3(String ip, ScanConfig config) {
        if (config.securityName() == null || config.securityName().isBlank()) {
            log.debug("SNMPv3 probe for {} skipped: securityName (логин) не задан", ip);
            return null;
        }

        TransportMapping<UdpAddress> transport = null;
        Snmp snmp = null;
        try {
            OctetString securityName = new OctetString(config.securityName());

            OID authProto = config.hasV3Auth() ? resolveAuthProtocol(config.authProtocol()) : null;
            OctetString authPass = config.hasV3Auth() ? new OctetString(config.authPassword()) : null;
            OID privProto = (authProto != null && config.hasV3Priv()) ? resolvePrivProtocol(config.privProtocol()) : null;
            OctetString privPass = privProto != null ? new OctetString(config.privPassword()) : null;

            int securityLevel = privProto != null ? SecurityLevel.AUTH_PRIV
                              : authProto != null ? SecurityLevel.AUTH_NOPRIV
                              : SecurityLevel.NOAUTH_NOPRIV;

            // Instance-local USM, чтобы не мутировать глобальный SecurityModels при параллельном скане
            USM usm = new USM(SecurityProtocols.getInstance(), new OctetString(MPv3.createLocalEngineID()), 0);
            usm.addUser(new UsmUser(securityName, authProto, authPass, privProto, privPass));

            MessageDispatcher dispatcher = new MessageDispatcherImpl();
            dispatcher.addMessageProcessingModel(new MPv3(usm));

            transport = new DefaultUdpTransportMapping();
            snmp = new Snmp(dispatcher, transport);
            transport.listen();

            UserTarget<Address> target = new UserTarget<>();
            target.setAddress(GenericAddress.parse("udp:" + ip + "/" + config.snmpPort()));
            target.setVersion(SnmpConstants.version3);
            target.setSecurityModel(SecurityModel.SECURITY_MODEL_USM);
            target.setSecurityLevel(securityLevel);
            target.setSecurityName(securityName);
            target.setRetries(snmpProbeRetries);
            target.setTimeout(snmpProbeTimeoutMs);

            ScopedPDU pdu = new ScopedPDU();
            addProbeBindings(pdu);

            ResponseEvent<Address> response = snmp.send(pdu, target);
            return buildResult(ip, response);
        } catch (Exception e) {
            log.debug("SNMPv3 probe failed for {}: {}", ip, e.getMessage());
            return null;
        } finally {
            if (snmp != null)      try { snmp.close();      } catch (Exception ignored) {}
            if (transport != null) try { transport.close(); } catch (Exception ignored) {}
        }
    }

    /** Разбирает ответ агента в DeviceProbeResult (общий для v1/v2c/v3). */
    private DeviceProbeResult buildResult(String ip, ResponseEvent<Address> response) {
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
    }

    private OID resolveAuthProtocol(String name) {
        if (name == null) return AuthSHA.ID;
        return switch (name.toUpperCase()) {
            case "MD5"             -> AuthMD5.ID;
            case "SHA256", "SHA-256" -> AuthHMAC192SHA256.ID;
            default                -> AuthSHA.ID;   // SHA-1
        };
    }

    private OID resolvePrivProtocol(String name) {
        if (name == null) return PrivAES128.ID;
        return switch (name.toUpperCase()) {
            case "DES"             -> PrivDES.ID;
            case "AES256", "AES-256" -> PrivAES256.ID;
            default                -> PrivAES128.ID;
        };
    }

    private DeviceProbeResult probeViaWinRM(String ip, String username, String password) {
        int[] ports = {winrmDefaultPort, winrmDefaultHttpsPort};
        for (int winrmPort : ports) {
            if (!isPortOpen(ip, winrmPort)) continue;

            WinRMAdapter winrm = new WinRMAdapter(ip, winrmPort, username, password, null, winrmCommandTimeoutMs);
            DeviceCommandTarget target = new DeviceCommandTarget(
                    ip, winrmPort, username, password, ConnectionProtocol.WINRM,
                    null, winrmPort == winrmDefaultHttpsPort, winrmPort == winrmDefaultHttpsPort);
            try {
                Map<String, Object> connResult = winrm.connect(target).get(winrmConnectTimeoutMs, TimeUnit.MILLISECONDS);
                if (!Boolean.TRUE.equals(connResult.get("success"))) continue;

                String cmd = "$os = Get-CimInstance Win32_OperatingSystem; " +
                             "$cs = Get-CimInstance Win32_ComputerSystem; " +
                             "Write-Output \"$($cs.DNSHostName)|$($os.Caption)|$($os.Version)|$($cs.Manufacturer)|$($cs.Model)\"";

                Map<String, Object> result = winrm.executeCommand(cmd).get(winrmCommandTimeoutMs, TimeUnit.MILLISECONDS);
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
                try { winrm.disconnect().get(winrmDisconnectTimeoutMs, TimeUnit.MILLISECONDS); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private DeviceProbeResult probeViaSsh(String ip, String username, String password) {
        SSHAdapter ssh = new SSHAdapter(ip, 22, username, password, sshSessionTimeoutMs);
        DeviceCommandTarget target = new DeviceCommandTarget(
                ip, 22, username, password, ConnectionProtocol.SSH, null, false, false);
        try {
            Map<String, Object> connResult = ssh.connect(target).get(sshConnectTimeoutMs, TimeUnit.MILLISECONDS);
            if (!Boolean.TRUE.equals(connResult.get("success"))) {
                log.debug("[{}] SSH-подключение не удалось: {}", ip, connResult.get("error"));
                return null;
            }
            log.debug("[{}] SSH-подключение успешно, собираю информацию", ip);

            String hostname = execSsh(ssh, "hostname 2>/dev/null", 5000);
            String unameA   = execSsh(ssh, "uname -a 2>/dev/null", 5000);
            log.debug("[{}] SSH: hostname='{}', uname='{}'", ip, hostname, truncate(unameA, 120));

            if (unameA != null && unameA.toLowerCase().contains("linux")) {
                log.debug("[{}] определён как Linux по uname", ip);
                return buildLinuxProbeData(ip, hostname, unameA, ssh);
            }

            log.debug("[{}] uname без 'linux' → пробую как Cisco", ip);
            return buildCiscoProbeData(ip, ssh);

        } catch (TimeoutException e) {
            log.debug("SSH probe timed out for {}", ip);
        } catch (Exception e) {
            log.debug("SSH probe failed for {}: {}", ip, e.getMessage());
        } finally {
            try { ssh.disconnect().get(sshDisconnectTimeoutMs, TimeUnit.MILLISECONDS); } catch (Exception ignored) {}
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

    // Типовые TCP-порты живых хостов: SSH, SMB, RDP, WinRM(http/https), HTTP/HTTPS, Telnet, JetDirect(принтеры)
    private static final int[] REACHABILITY_PORTS = {22, 445, 3389, 5985, 5986, 80, 443, 23, 9100};
    private static final int REACHABILITY_PORT_TIMEOUT_MS = 400;

    private boolean isPortOpen(String ip, int port) {
        return isPortOpen(ip, port, portScanTimeoutMs);
    }

    private boolean isPortOpen(String ip, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Хост считается «живым», если отвечает на ICMP (системный ping) ИЛИ открыт типовой TCP-порт.
     * ВАЖНО: используем системный ping, а не InetAddress.isReachable() — последний без root
     * почти всегда возвращает false (не может слать ICMP), из-за чего скан не находил ничего.
     * TCP-проверка дополнительно ловит хосты с заблокированным ICMP (например, Windows-фаервол).
     */
    private boolean isHostReachable(String ip) {
        if (systemPing(ip)) {
            log.debug("[{}] доступен по ICMP (ping)", ip);
            return true;
        }
        for (int port : REACHABILITY_PORTS) {
            if (isPortOpen(ip, port, REACHABILITY_PORT_TIMEOUT_MS)) {
                log.debug("[{}] доступен по TCP-порту {}", ip, port);
                return true;
            }
        }
        return false;
    }

    /**
     * Системный ICMP-ping (работает без root, в отличие от InetAddress.isReachable).
     * Linux-формат флагов: -c 1 (один пакет), -W 1 (таймаут ответа, сек).
     */
    private boolean systemPing(String ip) {
        String[] cmd = {"ping", "-c", "1", "-W", "1", ip};
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();

            // читаем вывод команды (ping с -c1 -W1 завершается сам ~за 1с)
            String output;
            try (var in = p.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            }

            boolean finished = p.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                log.info("[{}] ping: ТАЙМАУТ (>3s) | cmd='{}' | output='{}'",
                        ip, String.join(" ", cmd), output);
                return false;
            }

            int code = p.exitValue();
            boolean ok = code == 0;
            String oneLine = output.replace("\n", " ⏎ ");
            if (ok) {
                log.info("[{}] ping: OK (exit=0) | {}", ip, oneLine);
            } else {
                // exit 1 = нет ответа; exit 2 = ошибка (нет прав/нет хоста/нет команды и т.п.)
                log.info("[{}] ping: НЕ ПРОШЁЛ (exit={}) | cmd='{}' | output='{}'",
                        ip, code, String.join(" ", cmd), oneLine);
            }
            return ok;
        } catch (Exception e) {
            // сюда попадаем, если самой команды ping нет в PATH / её нельзя запустить
            log.warn("[{}] ping: НЕ УДАЛОСЬ ЗАПУСТИТЬ команду '{}' — {}: {}",
                    ip, String.join(" ", cmd), e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    /**
     * Обратный DNS (PTR): возвращает доменное имя хоста или сам IP, если PTR-записи нет.
     * Используется, когда устройство опознано только по открытому порту (нет SNMP/SSH-имени).
     */
    private String reverseHost(String ip) {
        try {
            String name = InetAddress.getByName(ip).getCanonicalHostName();
            if (name != null && !name.isBlank() && !name.equals(ip)) {
                return name;
            }
        } catch (Exception ignored) {
            // нет PTR / DNS недоступен — оставляем IP
        }
        return ip;
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
