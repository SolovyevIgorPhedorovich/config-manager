package com.uniikm.configmanager.config.command;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * Генерирует блок Cisco IOS CLI команд из JSON-конфигурации.
 *
 * JSON-структура:
 *   hostname, domainName, bannerMotd, enableSecret,
 *   sshVersion, sshTimeout, vtyTimeout, vtyAclIn,
 *   ntpServer, ntpSourceInterface,
 *   snmpEnabled, snmpCommunityRo, snmpCommunityRw, snmpServer,
 *   syslogServer, syslogLevel,
 *   dhcpSnoopingEnabled, dhcpSnoopingVlans,
 *   vlans: [{id, name}],
 *   sviInterfaces: [{vlan, ipAddress, subnetMask, description}],
 *   interfaces: [{
 *     name, description, role, mode, vlanId, nativeVlan, allowedVlans,
 *     speed, duplex, shutdown, portfast, bpduGuard, bpduFilter,
 *     portSecurity, stormControl, stormControlLevel, dhcpTrusted
 *   }],
 *   defaultGateway,
 *   staticRoutes: [{network, mask, nextHop}],
 *   saveToMemory
 */
@Component
public class CiscoConfigCommandGenerator implements ConfigCommandGenerator {

    @Override
    public String generateCommand(JsonNode config) {
        StringBuilder sb = new StringBuilder();
        sb.append("configure terminal\n");

        appendSystemConfig(sb, config);
        appendSshVtyConfig(sb, config);
        appendNtpConfig(sb, config);
        appendSnmpConfig(sb, config);
        appendSyslogConfig(sb, config);
        appendDhcpSnoopingConfig(sb, config);
        appendVlanConfig(sb, config);
        appendInterfaceConfig(sb, config);
        appendSviConfig(sb, config);
        appendRoutingConfig(sb, config);

        sb.append("end\n");

        if (config.has("saveToMemory") && config.get("saveToMemory").asBoolean()) {
            sb.append("write memory\n");
        }

        return sb.toString();
    }

    // ── Система ──────────────────────────────────────────────────────────────

    private void appendSystemConfig(StringBuilder sb, JsonNode c) {
        String hostname = str(c, "hostname");
        if (!hostname.isEmpty()) sb.append(" hostname ").append(hostname).append("\n");

        String domain = str(c, "domainName");
        if (!domain.isEmpty()) sb.append(" ip domain-name ").append(domain).append("\n");

        String banner = str(c, "bannerMotd");
        if (!banner.isEmpty()) {
            sb.append(" banner motd ^\n").append(banner).append("\n^\n");
        }

        String secret = str(c, "enableSecret");
        if (!secret.isEmpty()) sb.append(" enable secret ").append(secret).append("\n");

        // Сервис пароля и отключение Telnet на глобальном уровне
        sb.append(" service password-encryption\n");
        sb.append(" no ip http server\n");
        sb.append(" no ip http secure-server\n");
    }

    // ── SSH / VTY ─────────────────────────────────────────────────────────────

    private void appendSshVtyConfig(StringBuilder sb, JsonNode c) {
        int sshVer = c.has("sshVersion") ? c.get("sshVersion").asInt() : 2;
        sb.append(" ip ssh version ").append(sshVer).append("\n");

        if (c.has("sshTimeout")) {
            sb.append(" ip ssh time-out ").append(c.get("sshTimeout").asInt()).append("\n");
        }

        // Генерация RSA ключа нужна для SSH (идемпотентна, если ключ уже есть нужно использовать force)
        String domain = str(c, "domainName");
        if (!domain.isEmpty()) {
            sb.append(" crypto key generate rsa modulus 2048\n");
        }

        int vtyTimeout = c.has("vtyTimeout") ? c.get("vtyTimeout").asInt() : 10;
        String vtyAcl = str(c, "vtyAclIn");

        sb.append(" line vty 0 15\n");
        sb.append("  transport input ssh\n");
        sb.append("  login local\n");
        sb.append("  exec-timeout ").append(vtyTimeout).append(" 0\n");
        if (!vtyAcl.isEmpty()) {
            sb.append("  access-class ").append(vtyAcl).append(" in\n");
        }
        sb.append(" exit\n");

        sb.append(" line con 0\n");
        sb.append("  exec-timeout 5 0\n");
        sb.append("  logging synchronous\n");
        sb.append(" exit\n");
    }

    // ── NTP ───────────────────────────────────────────────────────────────────

    private void appendNtpConfig(StringBuilder sb, JsonNode c) {
        String ntpServer = str(c, "ntpServer");
        if (ntpServer.isEmpty()) return;

        sb.append(" ntp server ").append(ntpServer);
        String src = str(c, "ntpSourceInterface");
        if (!src.isEmpty()) sb.append(" source ").append(src);
        sb.append("\n");
        sb.append(" ntp update-calendar\n");
        sb.append(" clock timezone MSK 3 0\n");
    }

    // ── SNMP ──────────────────────────────────────────────────────────────────

    private void appendSnmpConfig(StringBuilder sb, JsonNode c) {
        if (!bool(c, "snmpEnabled")) return;

        String ro = str(c, "snmpCommunityRo");
        String rw = str(c, "snmpCommunityRw");
        String host = str(c, "snmpServer");

        if (!ro.isEmpty()) sb.append(" snmp-server community ").append(ro).append(" RO\n");
        if (!rw.isEmpty()) sb.append(" snmp-server community ").append(rw).append(" RW\n");
        if (!host.isEmpty()) {
            sb.append(" snmp-server host ").append(host).append(" traps ").append(ro.isEmpty() ? "public" : ro).append("\n");
            sb.append(" snmp-server enable traps\n");
        }
        sb.append(" snmp-server location \"Managed by ConfigManager\"\n");
    }

    // ── Syslog ────────────────────────────────────────────────────────────────

    private void appendSyslogConfig(StringBuilder sb, JsonNode c) {
        String syslogHost = str(c, "syslogServer");
        if (syslogHost.isEmpty()) return;

        sb.append(" logging on\n");
        sb.append(" logging host ").append(syslogHost).append("\n");
        String level = str(c, "syslogLevel");
        if (!level.isEmpty()) sb.append(" logging trap ").append(level).append("\n");
        sb.append(" logging buffered 16384 informational\n");
    }

    // ── DHCP Snooping ─────────────────────────────────────────────────────────

    private void appendDhcpSnoopingConfig(StringBuilder sb, JsonNode c) {
        if (!bool(c, "dhcpSnoopingEnabled")) return;

        sb.append(" ip dhcp snooping\n");
        String vlans = str(c, "dhcpSnoopingVlans");
        if (!vlans.isEmpty()) sb.append(" ip dhcp snooping vlan ").append(vlans).append("\n");
        sb.append(" no ip dhcp snooping information option\n");
    }

    // ── VLANs ─────────────────────────────────────────────────────────────────

    private void appendVlanConfig(StringBuilder sb, JsonNode c) {
        if (!c.has("vlans") || !c.get("vlans").isArray()) return;
        for (JsonNode vlan : c.get("vlans")) {
            if (!vlan.has("id")) continue;
            sb.append(" vlan ").append(vlan.get("id").asInt()).append("\n");
            String name = vlan.has("name") ? vlan.get("name").asText().trim() : "";
            if (!name.isEmpty()) sb.append("  name ").append(name).append("\n");
            sb.append(" exit\n");
        }
    }

    // ── Физические интерфейсы ─────────────────────────────────────────────────

    private void appendInterfaceConfig(StringBuilder sb, JsonNode c) {
        if (!c.has("interfaces") || !c.get("interfaces").isArray()) return;

        for (JsonNode iface : c.get("interfaces")) {
            String ifName = str(iface, "name");
            if (ifName.isEmpty()) continue;

            sb.append(" interface ").append(ifName).append("\n");

            // Описание
            String desc = str(iface, "description");
            if (!desc.isEmpty()) sb.append("  description ").append(desc).append("\n");

            // Роль → дополнительные флаги уже заданы ниже полями
            String mode = str(iface, "mode");
            if (mode.isEmpty()) mode = "access";

            // Режим коммутации
            if ("trunk".equals(mode)) {
                sb.append("  switchport mode trunk\n");
                int nativeVlan = iface.has("nativeVlan") ? iface.get("nativeVlan").asInt() : 1;
                sb.append("  switchport trunk native vlan ").append(nativeVlan).append("\n");
                String allowed = str(iface, "allowedVlans");
                if (!allowed.isEmpty()) {
                    sb.append("  switchport trunk allowed vlan ").append(allowed).append("\n");
                } else {
                    sb.append("  switchport trunk allowed vlan all\n");
                }
            } else {
                sb.append("  switchport mode access\n");
                if (iface.has("vlanId")) {
                    sb.append("  switchport access vlan ").append(iface.get("vlanId").asInt()).append("\n");
                }
            }

            // Скорость и дуплекс
            String speed = str(iface, "speed");
            if (!speed.isEmpty() && !"auto".equals(speed)) {
                sb.append("  speed ").append(speed).append("\n");
            }
            String duplex = str(iface, "duplex");
            if (!duplex.isEmpty() && !"auto".equals(duplex)) {
                sb.append("  duplex ").append(duplex).append("\n");
            }

            // Spanning Tree
            if (bool(iface, "portfast") && !"trunk".equals(mode)) {
                sb.append("  spanning-tree portfast\n");
            }
            if (bool(iface, "bpduGuard")) {
                sb.append("  spanning-tree bpduguard enable\n");
            }
            if (bool(iface, "bpduFilter")) {
                sb.append("  spanning-tree bpdufilter enable\n");
            }

            // Port Security (только access)
            if (bool(iface, "portSecurity") && !"trunk".equals(mode)) {
                sb.append("  switchport port-security\n");
                sb.append("  switchport port-security maximum 1\n");
                sb.append("  switchport port-security violation restrict\n");
                sb.append("  switchport port-security mac-address sticky\n");
            }

            // Storm Control
            if (bool(iface, "stormControl")) {
                int lvl = iface.has("stormControlLevel") ? iface.get("stormControlLevel").asInt() : 10;
                sb.append("  storm-control broadcast level ").append(lvl).append("\n");
                sb.append("  storm-control multicast level ").append(lvl).append("\n");
                sb.append("  storm-control action shutdown\n");
                sb.append("  storm-control action trap\n");
            }

            // DHCP Snooping Trust (для аплинков)
            if (bool(iface, "dhcpTrusted")) {
                sb.append("  ip dhcp snooping trust\n");
            }

            // Административное состояние
            if (bool(iface, "shutdown")) {
                sb.append("  shutdown\n");
            } else {
                sb.append("  no shutdown\n");
            }

            sb.append(" exit\n");
        }
    }

    // ── SVI (VLAN-интерфейсы с IP) ────────────────────────────────────────────

    private void appendSviConfig(StringBuilder sb, JsonNode c) {
        if (!c.has("sviInterfaces") || !c.get("sviInterfaces").isArray()) return;

        for (JsonNode svi : c.get("sviInterfaces")) {
            if (!svi.has("vlan")) continue;
            int vlan = svi.get("vlan").asInt();
            sb.append(" interface Vlan").append(vlan).append("\n");

            String desc = str(svi, "description");
            if (!desc.isEmpty()) sb.append("  description ").append(desc).append("\n");

            String ip = str(svi, "ipAddress");
            String mask = svi.has("subnetMask") ? svi.get("subnetMask").asText() : "255.255.255.0";
            if (!ip.isEmpty()) sb.append("  ip address ").append(ip).append(" ").append(mask).append("\n");

            sb.append("  no shutdown\n");
            sb.append(" exit\n");
        }
    }

    // ── Маршрутизация ─────────────────────────────────────────────────────────

    private void appendRoutingConfig(StringBuilder sb, JsonNode c) {
        String gw = str(c, "defaultGateway");
        if (!gw.isEmpty()) sb.append(" ip default-gateway ").append(gw).append("\n");

        if (c.has("staticRoutes") && c.get("staticRoutes").isArray()) {
            for (JsonNode route : c.get("staticRoutes")) {
                String net  = str(route, "network");
                String mask = str(route, "mask");
                String hop  = str(route, "nextHop");
                if (!net.isEmpty() && !mask.isEmpty() && !hop.isEmpty()) {
                    sb.append(" ip route ").append(net).append(" ").append(mask)
                      .append(" ").append(hop).append("\n");
                }
            }
        }
    }

    // ── Утилиты ───────────────────────────────────────────────────────────────

    private String str(JsonNode node, String key) {
        return node.has(key) ? node.get(key).asText("").trim() : "";
    }

    private boolean bool(JsonNode node, String key) {
        return node.has(key) && node.get(key).asBoolean();
    }
}
