package com.uniikm.configmanager.config.command;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class LinuxConfigCommandGenerator implements ConfigCommandGenerator {

    @Override
    public String generateCommand(JsonNode config) {
        StringBuilder script = new StringBuilder();
        script.append("#!/bin/bash\nset -e\n\n");

        // hostname
        if (config.has("hostname") && !config.get("hostname").asText().isEmpty()) {
            script.append("echo '").append(config.get("hostname").asText()).append("' > /etc/hostname\n");
            script.append("hostnamectl set-hostname ").append(config.get("hostname").asText()).append("\n");
        }

        // timezone
        if (config.has("timezone")) {
            script.append("timedatectl set-timezone ").append(config.get("timezone").asText()).append("\n");
        }

        // NTP
        if (config.has("ntpServers")) {
            String ntp = config.get("ntpServers").asText();
            script.append("sed -i 's/^pool.*/pool ").append(ntp).append("/' /etc/chrony.conf\n");
            script.append("systemctl restart chronyd\n");
        }

        // DNS
        if (config.has("dnsServers")) {
            String dns = config.get("dnsServers").asText();
            script.append("echo 'nameserver ").append(dns.replace(" ", "\nnameserver ")).append("' > /etc/resolv.conf\n");
        }

        // SSH port
        if (config.has("sshPort")) {
            int port = config.get("sshPort").asInt();
            script.append("sed -i 's/^#Port 22/Port ").append(port).append("/' /etc/ssh/sshd_config\n");
            script.append("sed -i 's/^Port 22/Port ").append(port).append("/' /etc/ssh/sshd_config\n");
        }
        if (config.has("sshPasswordAuth")) {
            boolean passAuth = config.get("sshPasswordAuth").asBoolean();
            script.append("sed -i 's/^#PasswordAuthentication yes/PasswordAuthentication ").append(passAuth ? "yes" : "no").append("/' /etc/ssh/sshd_config\n");
        }
        if (config.has("sshRootLogin")) {
            boolean rootLogin = config.get("sshRootLogin").asBoolean();
            script.append("sed -i 's/^#PermitRootLogin prohibit-password/PermitRootLogin ").append(rootLogin ? "yes" : "no").append("/' /etc/ssh/sshd_config\n");
        }
        script.append("systemctl restart sshd\n");

        // Firewall
        if (config.has("enableFirewall") && config.get("enableFirewall").asBoolean()) {
            script.append("systemctl enable --now firewalld\n");
            if (config.has("firewallRules")) {
                String rules = config.get("firewallRules").asText();
                for (String rule : rules.split("\n")) {
                    if (!rule.trim().isEmpty())
                        script.append("firewall-cmd --permanent ").append(rule).append("\n");
                }
                script.append("firewall-cmd --reload\n");
            }
        }

        // IP forwarding
        if (config.has("enableIpForward") && config.get("enableIpForward").asBoolean()) {
            script.append("echo 'net.ipv4.ip_forward=1' >> /etc/sysctl.conf\n");
            script.append("sysctl -p\n");
        }

        // SELinux
        if (config.has("selinuxMode")) {
            String mode = config.get("selinuxMode").asText();
            script.append("sed -i 's/^SELINUX=.*/SELINUX=").append(mode).append("/' /etc/selinux/config\n");
        }

        // auditd
        if (config.has("auditdEnabled") && !config.get("auditdEnabled").asBoolean()) {
            script.append("systemctl disable --now auditd\n");
        } else {
            script.append("systemctl enable --now auditd\n");
        }

        // fail2ban
        if (config.has("fail2banEnabled") && config.get("fail2banEnabled").asBoolean()) {
            script.append("systemctl enable --now fail2ban\n");
        }

        // max auth retries (через pam)
        if (config.has("maxAuthRetries")) {
            int retries = config.get("maxAuthRetries").asInt();
            script.append("echo 'auth required pam_tally2.so deny=").append(retries).append(" unlock_time=1800' >> /etc/pam.d/sshd\n");
        }

        // Создание пользователя
        if (config.has("createUser") && config.get("createUser").asBoolean() && config.has("newUsername")) {
            String user = config.get("newUsername").asText();
            script.append("useradd -m -s /bin/bash ").append(user).append("\n");
            if (config.has("newUserSudo") && config.get("newUserSudo").asBoolean()) {
                script.append("echo '").append(user).append(" ALL=(ALL) ALL' >> /etc/sudoers\n");
            }
        }

        // Node Exporter
        if (config.has("installNodeExporter") && config.get("installNodeExporter").asBoolean()) {
            script.append("curl -sL https://github.com/prometheus/node_exporter/releases/download/v1.6.0/node_exporter-1.6.0.linux-amd64.tar.gz | tar xz -C /opt\n");
            script.append("mv /opt/node_exporter-1.6.0.linux-amd64 /opt/node_exporter\n");
            script.append("cat > /etc/systemd/system/node_exporter.service <<EOF\n[Unit]\nDescription=Node Exporter\n[Service]\nExecStart=/opt/node_exporter/node_exporter\n[Install]\nWantedBy=multi-user.target\nEOF\n");
            script.append("systemctl daemon-reload && systemctl enable --now node_exporter\n");
        }

        script.append("echo 'Configuration applied successfully'\n");
        return script.toString();
    }
}