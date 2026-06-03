package com.uniikm.configmanager.config.command;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * Генерирует PowerShell-скрипт для настройки Windows через WinRM.
 *
 * createRestorePoint: true → создаёт точку восстановления перед изменениями
 */
@Component
public class WindowsConfigCommandGenerator implements ConfigCommandGenerator {

    @Override
    public String generateCommand(JsonNode config) {
        StringBuilder sb = new StringBuilder();
        sb.append("$ErrorActionPreference = 'Stop'\n");
        sb.append("$ProgressPreference = 'SilentlyContinue'\n\n");

        appendRestorePoint(sb, config);
        appendComputerName(sb, config);
        appendDomainJoin(sb, config);
        appendTimezone(sb, config);
        appendNtp(sb, config);
        appendNetworkSettings(sb, config);
        appendDns(sb, config);
        appendFirewall(sb, config);
        appendRdp(sb, config);
        appendSmb(sb, config);
        appendWinrm(sb, config);
        appendExecutionPolicy(sb, config);
        appendAutoLogon(sb, config);
        appendScreenLock(sb, config);
        appendPasswordPolicy(sb, config);
        appendLocalUsers(sb, config);
        appendServices(sb, config);
        appendWsus(sb, config);
        appendAuditPolicy(sb, config);
        appendMonitoring(sb, config);

        sb.append("\nWrite-Host 'Configuration applied successfully'\n");
        return "powershell.exe -NoProfile -ExecutionPolicy Bypass -Command \""
               + sb.toString().replace("\"", "\\\"").replace("\n", "; ") + "\"";
    }

    // ── Точка восстановления ──────────────────────────────────────────────────

    private void appendRestorePoint(StringBuilder sb, JsonNode c) {
        if (!bool(c, "createRestorePoint")) return;
        sb.append("# Create system restore point\n");
        sb.append("Enable-ComputerRestore -Drive 'C:\\' -ErrorAction SilentlyContinue\n");
        sb.append("Checkpoint-Computer -Description 'Before ConfigManager Apply' -RestorePointType 'MODIFY_SETTINGS' -ErrorAction SilentlyContinue\n");
    }

    // ── Имя компьютера ────────────────────────────────────────────────────────

    private void appendComputerName(StringBuilder sb, JsonNode c) {
        if (!bool(c, "changeComputerName")) return;
        String name = str(c, "newComputerName");
        if (name.isEmpty()) return;
        sb.append("Rename-Computer -NewName '").append(name).append("' -Force -PassThru -ErrorAction SilentlyContinue\n");
    }

    // ── Домен ─────────────────────────────────────────────────────────────────

    private void appendDomainJoin(StringBuilder sb, JsonNode c) {
        if (!bool(c, "joinDomain")) return;
        String domain = str(c, "domainName");
        String user   = str(c, "domainUser");
        String pass   = str(c, "domainPassword");
        if (domain.isEmpty() || user.isEmpty()) return;
        sb.append("$DomainCred = New-Object PSCredential('").append(user).append("', (ConvertTo-SecureString '").append(pass).append("' -AsPlainText -Force))\n");
        sb.append("Add-Computer -DomainName '").append(domain).append("' -Credential $DomainCred -Force -Restart -ErrorAction SilentlyContinue\n");
    }

    // ── Часовой пояс ──────────────────────────────────────────────────────────

    private void appendTimezone(StringBuilder sb, JsonNode c) {
        String tz = str(c, "timezone");
        if (!tz.isEmpty()) sb.append("Set-TimeZone -Id '").append(tz).append("'\n");
    }

    // ── NTP ───────────────────────────────────────────────────────────────────

    private void appendNtp(StringBuilder sb, JsonNode c) {
        String ntp = str(c, "ntpServer");
        if (ntp.isEmpty()) return;
        sb.append("w32tm /config /manualpeerlist:\"").append(ntp).append("\" /syncfromflags:manual /reliable:YES /update\n");
        sb.append("Restart-Service w32time\n");
        sb.append("w32tm /resync\n");
    }

    // ── Статический IP ────────────────────────────────────────────────────────

    private void appendNetworkSettings(StringBuilder sb, JsonNode c) {
        if (!bool(c, "setStaticIp")) return;
        String adapter   = str(c, "networkAdapter");
        String ipAddress = str(c, "ipAddress");
        String prefix    = str(c, "prefixLength");
        String gateway   = str(c, "gateway");
        if (ipAddress.isEmpty()) return;

        String adapterFilter = adapter.isEmpty()
            ? "Get-NetAdapter | Where-Object { $_.Status -eq 'Up' } | Select-Object -First 1 -ExpandProperty Name"
            : "'" + adapter + "'";
        sb.append("$Adapter = ").append(adapterFilter).append("\n");
        sb.append("$ExistingIP = Get-NetIPAddress -InterfaceAlias $Adapter -AddressFamily IPv4 -ErrorAction SilentlyContinue\n");
        sb.append("if ($ExistingIP) { Remove-NetIPAddress -InterfaceAlias $Adapter -AddressFamily IPv4 -Confirm:$false }\n");
        sb.append("New-NetIPAddress -InterfaceAlias $Adapter -IPAddress '").append(ipAddress).append("'");
        sb.append(" -PrefixLength ").append(prefix.isEmpty() ? "24" : prefix);
        if (!gateway.isEmpty()) sb.append(" -DefaultGateway '").append(gateway).append("'");
        sb.append("\n");
    }

    // ── DNS ───────────────────────────────────────────────────────────────────

    private void appendDns(StringBuilder sb, JsonNode c) {
        String dns = str(c, "dnsServers");
        if (dns.isEmpty()) return;
        sb.append("$Adapter = (Get-NetAdapter | Where-Object { $_.Status -eq 'Up' } | Select-Object -First 1).Name\n");
        sb.append("Set-DnsClientServerAddress -InterfaceAlias $Adapter -ServerAddresses @('")
          .append(dns.replace(" ", "','")).append("')\n");
    }

    // ── Брандмауэр ────────────────────────────────────────────────────────────

    private void appendFirewall(StringBuilder sb, JsonNode c) {
        if (c.has("firewallEnabled")) {
            boolean enabled = bool(c, "firewallEnabled");
            String profile  = str(c, "firewallProfile");
            String profileArg = profile.isEmpty() ? "All" : profile;
            sb.append("Set-NetFirewallProfile -Profile ").append(profileArg).append(" -Enabled ").append(enabled ? "True" : "False").append("\n");
        }
        // Открытые порты
        if (c.has("firewallOpenPorts") && c.get("firewallOpenPorts").isArray()) {
            for (JsonNode rule : c.get("firewallOpenPorts")) {
                int    port  = rule.has("port")     ? rule.get("port").asInt() : 0;
                String proto = rule.has("proto")    ? str(rule, "proto")       : "TCP";
                String name  = rule.has("name")     ? str(rule, "name")        : "Port " + port;
                String dir   = rule.has("direction")? str(rule, "direction")   : "Inbound";
                if (port == 0) continue;
                sb.append("New-NetFirewallRule -DisplayName '").append(name)
                  .append("' -Direction ").append(dir)
                  .append(" -Protocol ").append(proto.toUpperCase())
                  .append(" -LocalPort ").append(port)
                  .append(" -Action Allow -ErrorAction SilentlyContinue\n");
            }
        }
        if (bool(c, "enableWindowsDefender")) {
            sb.append("Set-MpPreference -DisableRealtimeMonitoring $false -ErrorAction SilentlyContinue\n");
        }
    }

    // ── RDP ───────────────────────────────────────────────────────────────────

    private void appendRdp(StringBuilder sb, JsonNode c) {
        if (!c.has("rdpEnabled")) return;
        boolean rdp = bool(c, "rdpEnabled");
        sb.append("Set-ItemProperty -Path 'HKLM:\\System\\CurrentControlSet\\Control\\Terminal Server' -Name 'fDenyTSConnections' -Value ").append(rdp ? "0" : "1").append("\n");
        if (rdp) {
            sb.append("Enable-NetFirewallRule -DisplayGroup 'Remote Desktop'\n");
            if (bool(c, "rdpNla")) {
                sb.append("Set-ItemProperty -Path 'HKLM:\\System\\CurrentControlSet\\Control\\Terminal Server\\WinStations\\RDP-Tcp' -Name 'UserAuthentication' -Value 1\n");
            }
            if (c.has("rdpPort") && c.get("rdpPort").asInt() != 3389) {
                sb.append("Set-ItemProperty -Path 'HKLM:\\System\\CurrentControlSet\\Control\\Terminal Server\\WinStations\\RDP-Tcp' -Name 'PortNumber' -Value ").append(c.get("rdpPort").asInt()).append("\n");
            }
        }
    }

    // ── SMB ───────────────────────────────────────────────────────────────────

    private void appendSmb(StringBuilder sb, JsonNode c) {
        if (!c.has("smbEnabled")) return;
        boolean smb = bool(c, "smbEnabled");
        if (!smb) {
            sb.append("Disable-WindowsOptionalFeature -Online -FeatureName SMB1Protocol -NoRestart -ErrorAction SilentlyContinue\n");
            sb.append("Set-SmbServerConfiguration -EnableSMB1Protocol $false -Force\n");
        } else {
            sb.append("Set-SmbServerConfiguration -EnableSMB2Protocol $true -Force\n");
        }
    }

    // ── WinRM ─────────────────────────────────────────────────────────────────

    private void appendWinrm(StringBuilder sb, JsonNode c) {
        if (bool(c, "configureWinrm")) {
            sb.append("winrm quickconfig -q\n");
            sb.append("winrm set winrm/config/winrs @{MaxMemoryPerShellMB='512'}\n");
            sb.append("Enable-PSRemoting -Force\n");
        }
    }

    // ── PowerShell Execution Policy ───────────────────────────────────────────

    private void appendExecutionPolicy(StringBuilder sb, JsonNode c) {
        String policy = str(c, "executionPolicy");
        if (!policy.isEmpty()) sb.append("Set-ExecutionPolicy ").append(policy).append(" -Force\n");
    }

    // ── Автовход ──────────────────────────────────────────────────────────────

    private void appendAutoLogon(StringBuilder sb, JsonNode c) {
        if (!bool(c, "autoLogonEnabled")) return;
        String user = str(c, "autoLogonUser");
        String pass = str(c, "autoLogonPassword");
        if (user.isEmpty()) return;
        String regPath = "HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Winlogon";
        sb.append("Set-ItemProperty -Path '").append(regPath).append("' -Name 'AutoAdminLogon' -Value '1'\n");
        sb.append("Set-ItemProperty -Path '").append(regPath).append("' -Name 'DefaultUserName' -Value '").append(user).append("'\n");
        sb.append("Set-ItemProperty -Path '").append(regPath).append("' -Name 'DefaultPassword' -Value '").append(pass).append("'\n");
    }

    // ── Экранная блокировка / Таймаут ─────────────────────────────────────────

    private void appendScreenLock(StringBuilder sb, JsonNode c) {
        if (c.has("inactivityTimeout")) {
            int minutes = c.get("inactivityTimeout").asInt();
            sb.append("powercfg /change standby-timeout-ac ").append(minutes).append("\n");
            if (minutes > 0) {
                sb.append("Set-ItemProperty -Path 'HKLM:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\System' -Name 'InactivityTimeoutSecs' -Value ").append(minutes * 60).append("\n");
            }
        }
    }

    // ── Парольная политика ────────────────────────────────────────────────────

    private void appendPasswordPolicy(StringBuilder sb, JsonNode c) {
        if (!c.has("passwordPolicy")) return;
        JsonNode pp = c.get("passwordPolicy");
        if (pp.has("minLength")) {
            sb.append("net accounts /minpwlen:").append(pp.get("minLength").asInt()).append("\n");
        }
        if (pp.has("maxAge")) {
            sb.append("net accounts /maxpwage:").append(pp.get("maxAge").asInt()).append("\n");
        }
        if (pp.has("lockoutThreshold")) {
            sb.append("net accounts /lockoutthreshold:").append(pp.get("lockoutThreshold").asInt()).append("\n");
        }
        if (pp.has("lockoutDuration")) {
            sb.append("net accounts /lockoutduration:").append(pp.get("lockoutDuration").asInt()).append("\n");
        }
    }

    // ── Локальные пользователи ────────────────────────────────────────────────

    private void appendLocalUsers(StringBuilder sb, JsonNode c) {
        if (!c.has("localUsers") || !c.get("localUsers").isArray()) return;
        for (JsonNode u : c.get("localUsers")) {
            String name = str(u, "name");
            if (name.isEmpty()) continue;
            String pass    = str(u, "password");
            boolean create = bool(u, "create");
            boolean admin  = bool(u, "admin");
            boolean disable = bool(u, "disabled");

            if (create) {
                sb.append("$SecPass = ConvertTo-SecureString '").append(pass).append("' -AsPlainText -Force\n");
                sb.append("New-LocalUser '").append(name).append("' -Password $SecPass -PasswordNeverExpires -ErrorAction SilentlyContinue\n");
                if (admin) {
                    sb.append("Add-LocalGroupMember -Group 'Administrators' -Member '").append(name).append("' -ErrorAction SilentlyContinue\n");
                }
            }
            if (disable) {
                sb.append("Disable-LocalUser -Name '").append(name).append("' -ErrorAction SilentlyContinue\n");
            }
        }
    }

    // ── Службы Windows ────────────────────────────────────────────────────────

    private void appendServices(StringBuilder sb, JsonNode c) {
        if (!c.has("services") || !c.get("services").isArray()) return;
        for (JsonNode svc : c.get("services")) {
            String name = str(svc, "name");
            if (name.isEmpty()) continue;
            String startType = str(svc, "startType");   // Automatic, Manual, Disabled
            String state     = str(svc, "state");        // Running, Stopped
            if (!startType.isEmpty()) {
                sb.append("Set-Service -Name '").append(name).append("' -StartupType '").append(startType).append("' -ErrorAction SilentlyContinue\n");
            }
            if ("Running".equalsIgnoreCase(state)) {
                sb.append("Start-Service -Name '").append(name).append("' -ErrorAction SilentlyContinue\n");
            } else if ("Stopped".equalsIgnoreCase(state)) {
                sb.append("Stop-Service -Name '").append(name).append("' -Force -ErrorAction SilentlyContinue\n");
            }
        }
    }

    // ── WSUS / Windows Update ─────────────────────────────────────────────────

    private void appendWsus(StringBuilder sb, JsonNode c) {
        String wsus = str(c, "wsusServer");
        if (!wsus.isEmpty()) {
            sb.append("$WUSettings = (New-Object -ComObject 'Microsoft.Update.AutoUpdate').Settings\n");
            sb.append("Set-ItemProperty -Path 'HKLM:\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsUpdate' -Name 'WUServer' -Value '").append(wsus).append("' -Force\n");
            sb.append("Set-ItemProperty -Path 'HKLM:\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsUpdate' -Name 'WUStatusServer' -Value '").append(wsus).append("' -Force\n");
            sb.append("Set-ItemProperty -Path 'HKLM:\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsUpdate\\AU' -Name 'UseWUServer' -Value 1 -Force\n");
            sb.append("Restart-Service wuauserv -ErrorAction SilentlyContinue\n");
        }
    }

    // ── Политика аудита ───────────────────────────────────────────────────────

    private void appendAuditPolicy(StringBuilder sb, JsonNode c) {
        if (!c.has("auditCategories") || !c.get("auditCategories").isArray()) return;
        for (JsonNode cat : c.get("auditCategories")) {
            sb.append("auditpol /set /subcategory:'").append(cat.asText()).append("' /success:enable /failure:enable\n");
        }
    }

    // ── Мониторинг ────────────────────────────────────────────────────────────

    private void appendMonitoring(StringBuilder sb, JsonNode c) {
        if (bool(c, "installZabbixAgent")) {
            String zabbixServer = str(c, "zabbixServer");
            sb.append("$ZabbixUrl = 'https://cdn.zabbix.com/zabbix/binaries/stable/7.0/7.0.0/zabbix_agent2-7.0.0-windows-amd64-openssl.msi'\n");
            sb.append("Invoke-WebRequest -Uri $ZabbixUrl -OutFile $env:TEMP\\zabbix_agent.msi -ErrorAction SilentlyContinue\n");
            sb.append("msiexec /i $env:TEMP\\zabbix_agent.msi /qn SERVER=").append(zabbixServer).append(" SERVERACTIVE=").append(zabbixServer).append(" -ErrorAction SilentlyContinue\n");
        }
    }

    private String str(JsonNode node, String key) {
        return node.has(key) ? node.get(key).asText("").trim() : "";
    }

    private boolean bool(JsonNode node, String key) {
        return node.has(key) && node.get(key).asBoolean();
    }
}
