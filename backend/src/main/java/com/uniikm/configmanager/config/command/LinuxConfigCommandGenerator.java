package com.uniikm.configmanager.config.command;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * Генерирует bash-скрипт для настройки Linux/Proxmox через SSH.
 *
 * Режим saveToMemory:
 *   true  → изменения пишутся в конфиг-файлы (сохраняются после перезагрузки)
 *   false → применяются только к работающей системе (после перезагрузки — сброс)
 */
@Component
public class LinuxConfigCommandGenerator implements ConfigCommandGenerator {

    @Override
    public String generateCommand(JsonNode config) {
        boolean persist = !config.has("saveToMemory") || config.get("saveToMemory").asBoolean();
        StringBuilder sb = new StringBuilder("#!/bin/bash\nset -e\n\n");

        appendHostname(sb, config, persist);
        appendTimezone(sb, config);
        appendNtp(sb, config, persist);
        appendDns(sb, config, persist);
        appendNetworkInterfaces(sb, config, persist);
        appendSysctl(sb, config, persist);
        appendSsh(sb, config, persist);
        appendFirewall(sb, config);
        appendSelinux(sb, config, persist);
        appendPackages(sb, config);
        appendServices(sb, config, persist);
        appendUsers(sb, config);
        appendCron(sb, config);
        appendMonitoring(sb, config, persist);
        appendBanner(sb, config, persist);

        sb.append("\necho 'Configuration applied successfully'\n");
        return sb.toString();
    }

    // ── Hostname ──────────────────────────────────────────────────────────────

    private void appendHostname(StringBuilder sb, JsonNode c, boolean persist) {
        String h = str(c, "hostname");
        if (h.isEmpty()) return;
        if (persist) {
            sb.append("hostnamectl set-hostname '").append(h).append("'\n");
        } else {
            sb.append("hostname '").append(h).append("'\n");
        }
    }

    // ── Timezone ──────────────────────────────────────────────────────────────

    private void appendTimezone(StringBuilder sb, JsonNode c) {
        String tz = str(c, "timezone");
        if (!tz.isEmpty()) sb.append("timedatectl set-timezone '").append(tz).append("'\n");
    }

    // ── NTP ───────────────────────────────────────────────────────────────────

    private void appendNtp(StringBuilder sb, JsonNode c, boolean persist) {
        String ntp = str(c, "ntpServers");
        if (ntp.isEmpty()) return;
        if (persist) {
            // chrony (RHEL) or timesyncd (Debian/Ubuntu)
            sb.append("if command -v chronyc &>/dev/null; then\n");
            sb.append("  sed -i '/^pool\\|^server/d' /etc/chrony.conf\n");
            for (String s : ntp.split("\\s+")) {
                sb.append("  echo 'server ").append(s).append(" iburst' >> /etc/chrony.conf\n");
            }
            sb.append("  systemctl restart chronyd 2>/dev/null || systemctl restart chrony\n");
            sb.append("elif command -v timedatectl &>/dev/null; then\n");
            sb.append("  sed -i 's/^#\\?NTP=.*/NTP=").append(ntp.replace(" ", " ")).append("/' /etc/systemd/timesyncd.conf\n");
            sb.append("  systemctl restart systemd-timesyncd\n");
            sb.append("fi\n");
        } else {
            sb.append("chronyc -a 'server ").append(ntp.split("\\s+")[0]).append("'\n");
        }
    }

    // ── DNS ───────────────────────────────────────────────────────────────────

    private void appendDns(StringBuilder sb, JsonNode c, boolean persist) {
        String dns = str(c, "dnsServers");
        if (dns.isEmpty()) return;
        if (persist) {
            sb.append("chattr -i /etc/resolv.conf 2>/dev/null || true\n");
            sb.append("truncate -s0 /etc/resolv.conf\n");
            String domain = str(c, "dnsDomain");
            if (!domain.isEmpty()) sb.append("echo 'domain ").append(domain).append("' >> /etc/resolv.conf\n");
            for (String s : dns.split("\\s+")) {
                sb.append("echo 'nameserver ").append(s).append("' >> /etc/resolv.conf\n");
            }
        } else {
            // Write resolv.conf still (it's a file) but note it's reset on dhcp renewal
            sb.append("# DNS: transient (reboot or DHCP renewal resets this)\n");
            sb.append("echo 'nameserver ").append(dns.split("\\s+")[0]).append("' > /etc/resolv.conf\n");
        }
    }

    // ── Сетевые интерфейсы ────────────────────────────────────────────────────

    private void appendNetworkInterfaces(StringBuilder sb, JsonNode c, boolean persist) {
        if (!c.has("networkInterfaces") || !c.get("networkInterfaces").isArray()) return;

        for (JsonNode iface : c.get("networkInterfaces")) {
            String name    = str(iface, "name");
            String address = str(iface, "address");
            String prefix  = str(iface, "prefix");
            String gateway = str(iface, "gateway");
            if (name.isEmpty() || address.isEmpty()) continue;

            String cidr = address + "/" + (prefix.isEmpty() ? "24" : prefix);

            if (persist) {
                // nmcli (NetworkManager, Debian/RHEL 8+)
                sb.append("if command -v nmcli &>/dev/null; then\n");
                sb.append("  CON=$(nmcli -t -f NAME,DEVICE con show | grep ':").append(name).append("$' | cut -d: -f1 | head -1)\n");
                sb.append("  [ -z \"$CON\" ] && CON='").append(name).append("'\n");
                sb.append("  nmcli con mod \"$CON\" ipv4.method manual ipv4.addresses '").append(cidr).append("'");
                if (!gateway.isEmpty()) sb.append(" ipv4.gateway '").append(gateway).append("'");
                sb.append("\n  nmcli con up \"$CON\"\n");
                sb.append("else\n");
                sb.append("  ip addr flush dev ").append(name).append("\n");
                sb.append("  ip addr add ").append(cidr).append(" dev ").append(name).append("\n");
                if (!gateway.isEmpty()) sb.append("  ip route add default via ").append(gateway).append("\n");
                sb.append("fi\n");
            } else {
                sb.append("ip addr flush dev ").append(name).append(" 2>/dev/null || true\n");
                sb.append("ip addr add ").append(cidr).append(" dev ").append(name).append("\n");
                sb.append("ip link set ").append(name).append(" up\n");
                if (!gateway.isEmpty()) {
                    sb.append("ip route replace default via ").append(gateway).append("\n");
                }
            }
        }
    }

    // ── Sysctl ────────────────────────────────────────────────────────────────

    private void appendSysctl(StringBuilder sb, JsonNode c, boolean persist) {
        // Явный список sysctl параметров
        if (c.has("sysctlParams") && c.get("sysctlParams").isArray()) {
            for (JsonNode param : c.get("sysctlParams")) {
                String key = str(param, "key");
                String val = str(param, "value");
                if (key.isEmpty()) continue;
                sb.append("sysctl -w ").append(key).append("=").append(val).append("\n");
                if (persist) {
                    sb.append("grep -qxF '").append(key).append("=").append(val)
                      .append("' /etc/sysctl.conf || echo '")
                      .append(key).append("=").append(val).append("' >> /etc/sysctl.conf\n");
                }
            }
        }
        // Короткий флаг IP forwarding
        if (bool(c, "enableIpForward")) {
            sb.append("sysctl -w net.ipv4.ip_forward=1\n");
            if (persist) sb.append("grep -qxF 'net.ipv4.ip_forward=1' /etc/sysctl.conf || echo 'net.ipv4.ip_forward=1' >> /etc/sysctl.conf\n");
        }
    }

    // ── SSH ───────────────────────────────────────────────────────────────────

    private void appendSsh(StringBuilder sb, JsonNode c, boolean persist) {
        boolean sshChanged = false;
        StringBuilder sshSb = new StringBuilder();

        if (c.has("sshPort")) {
            sshSb.append("sed -i 's/^#\\?Port .*/Port ").append(c.get("sshPort").asInt()).append("/' /etc/ssh/sshd_config\n");
            sshChanged = true;
        }
        if (c.has("sshPasswordAuth")) {
            String val = bool(c, "sshPasswordAuth") ? "yes" : "no";
            sshSb.append("sed -i 's/^#\\?PasswordAuthentication .*/PasswordAuthentication ").append(val).append("/' /etc/ssh/sshd_config\n");
            sshChanged = true;
        }
        if (c.has("sshRootLogin")) {
            String val = bool(c, "sshRootLogin") ? "yes" : "no";
            sshSb.append("sed -i 's/^#\\?PermitRootLogin .*/PermitRootLogin ").append(val).append("/' /etc/ssh/sshd_config\n");
            sshChanged = true;
        }
        if (c.has("sshMaxSessions")) {
            sshSb.append("sed -i 's/^#\\?MaxSessions .*/MaxSessions ").append(c.get("sshMaxSessions").asInt()).append("/' /etc/ssh/sshd_config\n");
            sshChanged = true;
        }
        if (c.has("sshAllowUsers") && !str(c, "sshAllowUsers").isEmpty()) {
            sshSb.append("grep -q '^AllowUsers' /etc/ssh/sshd_config && sed -i 's/^AllowUsers .*/AllowUsers ").append(str(c, "sshAllowUsers")).append("/' /etc/ssh/sshd_config || echo 'AllowUsers ").append(str(c, "sshAllowUsers")).append("' >> /etc/ssh/sshd_config\n");
            sshChanged = true;
        }
        if (c.has("sshAuthorizedKey") && !str(c, "sshAuthorizedKey").isEmpty()) {
            sshSb.append("mkdir -p ~/.ssh && chmod 700 ~/.ssh\n");
            sshSb.append("echo '").append(str(c, "sshAuthorizedKey")).append("' >> ~/.ssh/authorized_keys\n");
            sshSb.append("chmod 600 ~/.ssh/authorized_keys\n");
        }
        if (sshChanged) {
            sb.append(sshSb);
            sb.append("systemctl reload sshd 2>/dev/null || systemctl reload ssh\n");
        }
    }

    // ── Firewall ──────────────────────────────────────────────────────────────

    private void appendFirewall(StringBuilder sb, JsonNode c) {
        if (!bool(c, "enableFirewall")) return;

        sb.append("if command -v firewall-cmd &>/dev/null; then\n");
        sb.append("  systemctl enable --now firewalld\n");
        // Открытые TCP порты
        if (c.has("firewallOpenPorts") && c.get("firewallOpenPorts").isArray()) {
            for (JsonNode port : c.get("firewallOpenPorts")) {
                String proto = port.has("proto") ? port.get("proto").asText() : "tcp";
                sb.append("  firewall-cmd --permanent --add-port=").append(port.get("port").asInt()).append("/").append(proto).append("\n");
            }
        }
        // Произвольные правила
        if (c.has("firewallRules") && !str(c, "firewallRules").isEmpty()) {
            for (String rule : str(c, "firewallRules").split("\n")) {
                if (!rule.trim().isEmpty())
                    sb.append("  firewall-cmd --permanent ").append(rule.trim()).append("\n");
            }
        }
        sb.append("  firewall-cmd --reload\n");
        sb.append("elif command -v ufw &>/dev/null; then\n");
        sb.append("  ufw --force enable\n");
        if (c.has("firewallOpenPorts") && c.get("firewallOpenPorts").isArray()) {
            for (JsonNode port : c.get("firewallOpenPorts")) {
                String proto = port.has("proto") ? port.get("proto").asText() : "tcp";
                sb.append("  ufw allow ").append(port.get("port").asInt()).append("/").append(proto).append("\n");
            }
        }
        sb.append("fi\n");
    }

    // ── SELinux / AppArmor ────────────────────────────────────────────────────

    private void appendSelinux(StringBuilder sb, JsonNode c, boolean persist) {
        String mode = str(c, "selinuxMode");
        if (!mode.isEmpty() && persist) {
            sb.append("if [ -f /etc/selinux/config ]; then\n");
            sb.append("  sed -i 's/^SELINUX=.*/SELINUX=").append(mode).append("/' /etc/selinux/config\n");
            sb.append("  setenforce ").append("enforcing".equals(mode) ? "1" : "0").append(" 2>/dev/null || true\n");
            sb.append("fi\n");
        }
        if (bool(c, "auditdEnabled")) {
            sb.append("systemctl enable --now auditd 2>/dev/null || true\n");
        }
        if (bool(c, "fail2banEnabled")) {
            sb.append("systemctl enable --now fail2ban 2>/dev/null || true\n");
        }
        if (c.has("maxAuthRetries") && c.get("maxAuthRetries").asInt() > 0) {
            int retries = c.get("maxAuthRetries").asInt();
            sb.append("# PAM lockout after ").append(retries).append(" failed attempts\n");
            sb.append("grep -q 'pam_tally2' /etc/pam.d/sshd || echo 'auth required pam_tally2.so deny=").append(retries).append(" unlock_time=1800' >> /etc/pam.d/sshd\n");
        }
    }

    // ── Пакеты ────────────────────────────────────────────────────────────────

    private void appendPackages(StringBuilder sb, JsonNode c) {
        boolean hasInstall = c.has("installPackages") && c.get("installPackages").isArray() && c.get("installPackages").size() > 0;
        boolean hasRemove  = c.has("removePackages")  && c.get("removePackages").isArray()  && c.get("removePackages").size() > 0;
        if (!hasInstall && !hasRemove) return;

        sb.append("PM=apt-get; command -v dnf &>/dev/null && PM=dnf; command -v yum &>/dev/null && PM=yum\n");
        if (hasInstall) {
            sb.append("DEBIAN_FRONTEND=noninteractive $PM install -y");
            for (JsonNode pkg : c.get("installPackages")) sb.append(" ").append(pkg.asText().trim());
            sb.append("\n");
        }
        if (hasRemove) {
            sb.append("DEBIAN_FRONTEND=noninteractive $PM remove -y");
            for (JsonNode pkg : c.get("removePackages")) sb.append(" ").append(pkg.asText().trim());
            sb.append("\n");
        }
    }

    // ── Службы ────────────────────────────────────────────────────────────────

    private void appendServices(StringBuilder sb, JsonNode c, boolean persist) {
        if (!c.has("services") || !c.get("services").isArray()) return;
        for (JsonNode svc : c.get("services")) {
            String name    = str(svc, "name");
            boolean enable = svc.has("enabled") && svc.get("enabled").asBoolean();
            boolean start  = !svc.has("running") || svc.get("running").asBoolean();
            if (name.isEmpty()) continue;
            if (persist) {
                sb.append("systemctl ").append(enable ? "enable" : "disable").append(" ").append(name).append(" 2>/dev/null || true\n");
            }
            sb.append("systemctl ").append(start ? "start" : "stop").append(" ").append(name).append(" 2>/dev/null || true\n");
        }
    }

    // ── Пользователи ──────────────────────────────────────────────────────────

    private void appendUsers(StringBuilder sb, JsonNode c) {
        if (!c.has("users") || !c.get("users").isArray()) return;
        for (JsonNode u : c.get("users")) {
            String name = str(u, "name");
            if (name.isEmpty()) continue;
            boolean create = bool(u, "create");
            if (create) {
                sb.append("id '").append(name).append("' &>/dev/null || useradd -m -s /bin/bash '").append(name).append("'\n");
                if (u.has("password") && !str(u, "password").isEmpty()) {
                    sb.append("echo '").append(name).append(":").append(str(u, "password")).append("' | chpasswd\n");
                }
                if (bool(u, "sudo")) {
                    sb.append("usermod -aG sudo '").append(name).append("' 2>/dev/null || usermod -aG wheel '").append(name).append("'\n");
                }
                if (bool(u, "locked")) {
                    sb.append("usermod -L '").append(name).append("'\n");
                }
            }
        }
        // Старый формат (совместимость)
        if (bool(c, "createUser") && c.has("newUsername")) {
            String uname = str(c, "newUsername");
            sb.append("id '").append(uname).append("' &>/dev/null || useradd -m -s /bin/bash '").append(uname).append("'\n");
            if (bool(c, "newUserSudo")) {
                sb.append("usermod -aG sudo '").append(uname).append("' 2>/dev/null || usermod -aG wheel '").append(uname).append("'\n");
            }
        }
    }

    // ── Cron ──────────────────────────────────────────────────────────────────

    private void appendCron(StringBuilder sb, JsonNode c) {
        if (!c.has("cronJobs") || !c.get("cronJobs").isArray()) return;
        for (JsonNode job : c.get("cronJobs")) {
            String schedule = str(job, "schedule");
            String cmd      = str(job, "command");
            String user     = job.has("user") ? str(job, "user") : "root";
            if (schedule.isEmpty() || cmd.isEmpty()) continue;
            sb.append("(crontab -u ").append(user).append(" -l 2>/dev/null; echo '")
              .append(schedule).append(" ").append(cmd).append("') | crontab -u ").append(user).append(" -\n");
        }
    }

    // ── Мониторинг ────────────────────────────────────────────────────────────

    private void appendMonitoring(StringBuilder sb, JsonNode c, boolean persist) {
        if (bool(c, "installNodeExporter")) {
            sb.append("NODE_VER=1.8.0\n");
            sb.append("curl -sL https://github.com/prometheus/node_exporter/releases/download/v${NODE_VER}/node_exporter-${NODE_VER}.linux-amd64.tar.gz | tar xz -C /tmp\n");
            sb.append("mv /tmp/node_exporter-${NODE_VER}.linux-amd64/node_exporter /usr/local/bin/\n");
            sb.append("cat > /etc/systemd/system/node_exporter.service <<'EOF'\n");
            sb.append("[Unit]\nDescription=Node Exporter\n[Service]\nExecStart=/usr/local/bin/node_exporter\n[Install]\nWantedBy=multi-user.target\nEOF\n");
            sb.append("systemctl daemon-reload\n");
            if (persist) sb.append("systemctl enable node_exporter\n");
            sb.append("systemctl start node_exporter\n");
        }
        if (bool(c, "installZabbixAgent")) {
            String zabbixServer = str(c, "zabbixServer");
            sb.append("PM=apt-get; command -v dnf &>/dev/null && PM=dnf\n");
            sb.append("DEBIAN_FRONTEND=noninteractive $PM install -y zabbix-agent 2>/dev/null || true\n");
            if (!zabbixServer.isEmpty()) {
                sb.append("sed -i 's/^Server=.*/Server=").append(zabbixServer).append("/' /etc/zabbix/zabbix_agentd.conf\n");
                sb.append("sed -i 's/^ServerActive=.*/ServerActive=").append(zabbixServer).append("/' /etc/zabbix/zabbix_agentd.conf\n");
            }
            if (persist) sb.append("systemctl enable zabbix-agent\n");
            sb.append("systemctl restart zabbix-agent\n");
        }
    }

    // ── Banner ────────────────────────────────────────────────────────────────

    private void appendBanner(StringBuilder sb, JsonNode c, boolean persist) {
        String banner = str(c, "bannerText");
        if (banner.isEmpty() || !persist) return;
        sb.append("cat > /etc/issue.net <<'BANNER_EOF'\n").append(banner).append("\nBANNER_EOF\n");
        sb.append("sed -i 's|^#\\?Banner .*|Banner /etc/issue.net|' /etc/ssh/sshd_config\n");
    }

    private String str(JsonNode node, String key) {
        return node.has(key) ? node.get(key).asText("").trim() : "";
    }

    private boolean bool(JsonNode node, String key) {
        return node.has(key) && node.get(key).asBoolean();
    }
}
