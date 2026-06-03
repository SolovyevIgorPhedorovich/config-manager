package com.uniikm.configmanager.config.command;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * Генерирует bash-скрипт для настройки Proxmox VE через SSH.
 * Использует pvesh CLI, pveum, pvesm и стандартные Linux-команды.
 *
 * saveToMemory: true  → изменения сохраняются (pvesh + config files)
 *               false → только транзиентные изменения где возможно
 */
@Component
public class ProxmoxConfigCommandGenerator implements ConfigCommandGenerator {

    @Override
    public String generateCommand(JsonNode config) {
        boolean persist = !config.has("saveToMemory") || config.get("saveToMemory").asBoolean();
        StringBuilder sb = new StringBuilder("#!/bin/bash\nset -e\n\n");

        appendSystemConfig(sb, config, persist);
        appendDatacenterConfig(sb, config, persist);
        appendNetworkBridges(sb, config, persist);
        appendStorage(sb, config);
        appendUsers(sb, config);
        appendVm(sb, config);
        appendContainer(sb, config);
        appendBackup(sb, config, persist);
        appendMonitoring(sb, config, persist);

        sb.append("\necho 'Proxmox configuration applied successfully'\n");
        return sb.toString();
    }

    // ── Системные настройки Proxmox-хоста ────────────────────────────────────

    private void appendSystemConfig(StringBuilder sb, JsonNode c, boolean persist) {
        String hostname = str(c, "hostname");
        if (!hostname.isEmpty()) {
            if (persist) sb.append("hostnamectl set-hostname '").append(hostname).append("'\n");
            else         sb.append("hostname '").append(hostname).append("'\n");
        }

        String tz = str(c, "timezone");
        if (!tz.isEmpty()) sb.append("timedatectl set-timezone '").append(tz).append("'\n");

        String ntpServer = str(c, "ntpServer");
        if (!ntpServer.isEmpty() && persist) {
            sb.append("sed -i '/^server\\|^pool/d' /etc/chrony/chrony.conf 2>/dev/null || sed -i '/^server\\|^pool/d' /etc/chrony.conf 2>/dev/null\n");
            sb.append("echo 'server ").append(ntpServer).append(" iburst' >> /etc/chrony/chrony.conf 2>/dev/null || echo 'server ").append(ntpServer).append(" iburst' >> /etc/chrony.conf\n");
            sb.append("systemctl restart chronyd 2>/dev/null || systemctl restart chrony\n");
        }

        String dns = str(c, "dnsServers");
        if (!dns.isEmpty()) {
            sb.append("# DNS configuration\n");
            sb.append("pvesh set /nodes/$(hostname)/dns -dns1 ").append(dns.split("\\s+")[0]);
            if (dns.split("\\s+").length > 1) sb.append(" -dns2 ").append(dns.split("\\s+")[1]);
            sb.append("\n");
        }

        String emailTo = str(c, "emailTo");
        if (!emailTo.isEmpty() && persist) {
            sb.append("pvesh set /nodes/$(hostname)/subscription  2>/dev/null || true\n");
            sb.append("pvesh set /cluster/options -email_from '").append(emailTo).append("' 2>/dev/null || true\n");
        }
    }

    // ── Настройки датацентра ──────────────────────────────────────────────────

    private void appendDatacenterConfig(StringBuilder sb, JsonNode c, boolean persist) {
        if (!persist) return;

        StringBuilder dcConfig = new StringBuilder();
        String keyboard = str(c, "keyboard");
        if (!keyboard.isEmpty()) dcConfig.append("keyboard: ").append(keyboard).append("\n");
        String language = str(c, "language");
        if (!language.isEmpty()) dcConfig.append("language: ").append(language).append("\n");
        String console = str(c, "consoleViewer");
        if (!console.isEmpty()) dcConfig.append("console: ").append(console).append("\n");
        String maxWorkers = str(c, "maxWorkers");
        if (!maxWorkers.isEmpty()) dcConfig.append("max_workers: ").append(maxWorkers).append("\n");

        if (dcConfig.length() > 0) {
            sb.append("cat > /etc/pve/datacenter.cfg <<'DC_EOF'\n")
              .append(dcConfig).append("DC_EOF\n");
        }

        String httpProxy = str(c, "httpProxy");
        if (!httpProxy.isEmpty()) {
            sb.append("pvesh set /cluster/options -http_proxy '").append(httpProxy).append("' 2>/dev/null || true\n");
        }
    }

    // ── Сетевые мосты (bridges) ───────────────────────────────────────────────

    private void appendNetworkBridges(StringBuilder sb, JsonNode c, boolean persist) {
        if (!c.has("bridges") || !c.get("bridges").isArray()) return;
        for (JsonNode br : c.get("bridges")) {
            String name    = str(br, "name");
            String ports   = str(br, "ports");
            String address = str(br, "address");
            String gateway = str(br, "gateway");
            if (name.isEmpty()) continue;

            if (persist) {
                sb.append("# Bridge: ").append(name).append("\n");
                sb.append("ip link add name ").append(name).append(" type bridge 2>/dev/null || true\n");
                if (!ports.isEmpty()) sb.append("ip link set ").append(ports).append(" master ").append(name).append("\n");
                if (!address.isEmpty()) sb.append("ip addr add ").append(address).append(" dev ").append(name).append("\n");
                sb.append("ip link set ").append(name).append(" up\n");
                if (!gateway.isEmpty()) sb.append("ip route replace default via ").append(gateway).append("\n");
            }
        }
    }

    // ── Хранилище ─────────────────────────────────────────────────────────────

    private void appendStorage(StringBuilder sb, JsonNode c) {
        if (!c.has("storages") || !c.get("storages").isArray()) return;
        for (JsonNode st : c.get("storages")) {
            String name = str(st, "name");
            String type = str(st, "type");
            if (name.isEmpty() || type.isEmpty()) continue;

            sb.append("pvesm status 2>/dev/null | grep -q '").append(name).append("' && echo 'Storage ").append(name).append(" already exists' || \\\n");
            sb.append("pvesm add ").append(type).append(" ").append(name);

            switch (type) {
                case "dir"     -> sb.append(" --path '").append(str(st, "path")).append("'");
                case "nfs"     -> sb.append(" --server ").append(str(st, "server")).append(" --export '").append(str(st, "export")).append("'");
                case "cifs"    -> sb.append(" --server ").append(str(st, "server")).append(" --share '").append(str(st, "share")).append("'");
                case "lvmthin" -> sb.append(" --thinpool ").append(str(st, "thinpool")).append(" --vgname ").append(str(st, "vgname"));
                case "zfspool" -> sb.append(" --pool ").append(str(st, "pool"));
                case "rbd"     -> sb.append(" --monhost ").append(str(st, "monhost")).append(" --pool ").append(str(st, "pool"));
            }

            if (st.has("content") && st.get("content").isArray()) {
                StringBuilder content = new StringBuilder();
                for (JsonNode ct : st.get("content")) {
                    if (content.length() > 0) content.append(",");
                    content.append(ct.asText());
                }
                sb.append(" --content ").append(content);
            }
            sb.append("\n");

            // Retention policy for backup storage
            if (st.has("pruneKeepLast") && st.get("pruneKeepLast").asInt() > 0) {
                sb.append("pvesm set ").append(name)
                  .append(" --prune-backups keep-last=").append(st.get("pruneKeepLast").asInt()).append("\n");
            }
        }
    }

    // ── Пользователи ─────────────────────────────────────────────────────────

    private void appendUsers(StringBuilder sb, JsonNode c) {
        if (!c.has("pveUsers") || !c.get("pveUsers").isArray()) return;
        for (JsonNode u : c.get("pveUsers")) {
            String userid = str(u, "userid");   // e.g. john@pve
            if (userid.isEmpty()) continue;

            String password = str(u, "password");
            boolean enabled = !u.has("enabled") || u.get("enabled").asBoolean();

            sb.append("pveum user add '").append(userid).append("' --enable ").append(enabled ? 1 : 0);
            if (!str(u, "email").isEmpty())   sb.append(" --email '").append(str(u, "email")).append("'");
            if (!str(u, "comment").isEmpty()) sb.append(" --comment '").append(str(u, "comment")).append("'");
            sb.append(" 2>/dev/null || pveum user modify '").append(userid).append("' --enable ").append(enabled ? 1 : 0).append("\n");

            if (!password.isEmpty()) {
                sb.append("echo '").append(userid).append(":").append(password).append("' | pveum passwd '").append(userid).append("' 2>/dev/null || true\n");
            }

            String role = str(u, "role");
            String rolePath = str(u, "rolePath");
            if (!role.isEmpty()) {
                String path = rolePath.isEmpty() ? "/" : rolePath;
                sb.append("pveum acl modify '").append(path).append("' --users '").append(userid).append("' --roles '").append(role).append("'\n");
            }

            String group = str(u, "group");
            if (!group.isEmpty()) {
                sb.append("pveum group add '").append(group).append("' 2>/dev/null || true\n");
                sb.append("pveum user modify '").append(userid).append("' --groups '").append(group).append("'\n");
            }
        }
    }

    // ── Создание VM (QEMU) ────────────────────────────────────────────────────

    private void appendVm(StringBuilder sb, JsonNode c) {
        if (!c.has("createVm") || !c.get("createVm").asBoolean()) return;

        int    vmId      = c.has("vmId")      ? c.get("vmId").asInt()      : 100;
        String vmName    = str(c, "vmName");
        String vmNode    = str(c, "vmNode");
        int    memory    = c.has("vmMemory")  ? c.get("vmMemory").asInt()  : 2048;
        int    cores     = c.has("vmCores")   ? c.get("vmCores").asInt()   : 2;
        int    sockets   = c.has("vmSockets") ? c.get("vmSockets").asInt() : 1;
        String storage   = str(c, "vmDiskStorage");
        int    diskSize  = c.has("vmDiskSize") ? c.get("vmDiskSize").asInt() : 32;
        String bridge    = c.has("vmNetBridge") ? str(c, "vmNetBridge") : "vmbr0";
        String netModel  = c.has("vmNetModel")  ? str(c, "vmNetModel")  : "virtio";
        String bios      = c.has("vmBios")      ? str(c, "vmBios")      : "seabios";

        sb.append("# Create QEMU VM\n");
        sb.append("pvesh get /nodes/").append(vmNode.isEmpty() ? "$(hostname)" : vmNode)
          .append("/qemu/").append(vmId).append("/status/current 2>/dev/null && echo 'VM ").append(vmId).append(" already exists' || \\\n");
        sb.append("pvesh create /nodes/").append(vmNode.isEmpty() ? "$(hostname)" : vmNode).append("/qemu");
        sb.append(" -vmid ").append(vmId);
        if (!vmName.isEmpty()) sb.append(" -name '").append(vmName).append("'");
        sb.append(" -memory ").append(memory);
        sb.append(" -cores ").append(cores);
        sb.append(" -sockets ").append(sockets);
        sb.append(" -bios ").append(bios);
        sb.append(" -net0 ").append(netModel).append(",bridge=").append(bridge);
        if (!storage.isEmpty()) {
            sb.append(" -scsi0 ").append(storage).append(":").append(diskSize);
            sb.append(" -scsihw virtio-scsi-pci");
        }
        sb.append(" -ostype l26 -agent enabled=1\n");
    }

    // ── Создание контейнера (LXC) ─────────────────────────────────────────────

    private void appendContainer(StringBuilder sb, JsonNode c) {
        if (!c.has("createCt") || !c.get("createCt").asBoolean()) return;

        int    ctId       = c.has("ctId")      ? c.get("ctId").asInt()     : 200;
        String ctName     = str(c, "ctName");
        String ctTemplate = str(c, "ctTemplate");
        String ctStorage  = str(c, "ctStorage");
        int    ctMemory   = c.has("ctMemory")  ? c.get("ctMemory").asInt() : 1024;
        int    ctSwap     = c.has("ctSwap")    ? c.get("ctSwap").asInt()   : 512;
        int    ctCores    = c.has("ctCores")   ? c.get("ctCores").asInt()  : 1;
        int    ctDisk     = c.has("ctDiskSize")? c.get("ctDiskSize").asInt(): 8;
        String ctBridge   = str(c, "ctBridge");
        boolean unpriv    = !c.has("ctUnprivileged") || c.get("ctUnprivileged").asBoolean();
        String ctPassword = str(c, "ctPassword");

        sb.append("# Create LXC Container\n");
        sb.append("pvesh get /nodes/$(hostname)/lxc/").append(ctId).append("/status/current 2>/dev/null && echo 'CT ").append(ctId).append(" already exists' || \\\n");
        sb.append("pvesh create /nodes/$(hostname)/lxc");
        sb.append(" -vmid ").append(ctId);
        if (!ctName.isEmpty())     sb.append(" -hostname '").append(ctName).append("'");
        if (!ctTemplate.isEmpty()) sb.append(" -ostemplate '").append(ctStorage.isEmpty() ? "local" : ctStorage).append(":vztmpl/").append(ctTemplate).append("'");
        sb.append(" -memory ").append(ctMemory);
        sb.append(" -swap ").append(ctSwap);
        sb.append(" -cores ").append(ctCores);
        sb.append(" -unprivileged ").append(unpriv ? 1 : 0);
        sb.append(" -rootfs ").append(ctStorage.isEmpty() ? "local" : ctStorage).append(":").append(ctDisk);
        sb.append(" -net0 name=eth0,bridge=").append(ctBridge.isEmpty() ? "vmbr0" : ctBridge).append(",ip=dhcp");
        if (!ctPassword.isEmpty()) sb.append(" -password '").append(ctPassword).append("'");
        sb.append(" -start 1\n");
    }

    // ── Резервное копирование ─────────────────────────────────────────────────

    private void appendBackup(StringBuilder sb, JsonNode c, boolean persist) {
        if (!persist) return;
        String backupStorage = str(c, "backupStorage");
        String backupSchedule = str(c, "backupSchedule");
        if (backupStorage.isEmpty() && backupSchedule.isEmpty()) return;

        sb.append("# Backup job configuration\n");
        if (!backupStorage.isEmpty() && !backupSchedule.isEmpty()) {
            sb.append("pvesh create /cluster/backup");
            sb.append(" -storage '").append(backupStorage).append("'");
            sb.append(" -schedule '").append(backupSchedule).append("'");
            sb.append(" -all 1");
            sb.append(" -compress lzo");
            sb.append(" -mode snapshot 2>/dev/null || true\n");
        }
    }

    // ── Мониторинг ────────────────────────────────────────────────────────────

    private void appendMonitoring(StringBuilder sb, JsonNode c, boolean persist) {
        if (bool(c, "enablePrometheusMetrics")) {
            sb.append("# Install pve-exporter for Prometheus\n");
            sb.append("pip3 install prometheus-pve-exporter 2>/dev/null || pip install prometheus-pve-exporter 2>/dev/null || true\n");
        }
    }

    private String str(JsonNode node, String key) {
        return node.has(key) ? node.get(key).asText("").trim() : "";
    }

    private boolean bool(JsonNode node, String key) {
        return node.has(key) && node.get(key).asBoolean();
    }
}
