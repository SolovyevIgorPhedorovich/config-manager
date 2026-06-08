package com.uniikm.configmanager.config.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Уровень 1: проверка генерируемого Cisco IOS CLI без реального устройства.
 * Генератор — обычный класс без Spring-зависимостей, поэтому тест быстрый и
 * не требует БД/Redis. Запуск:
 *   ./gradlew test --tests '*CiscoConfigCommandGeneratorTest*'
 * Полный сгенерированный CLI печатается в stdout (увидеть: --info / -i).
 */
class CiscoConfigCommandGeneratorTest {

    private final CiscoConfigCommandGenerator generator = new CiscoConfigCommandGenerator();
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String SAMPLE = """
        {
          "hostname": "sw-core-01",
          "domainName": "lab.local",
          "enableSecret": "S3cr3t!",
          "bannerMotd": "Authorized access only",
          "sshVersion": 2,
          "sshTimeout": 60,
          "vtyTimeout": 10,
          "ntpServer": "192.168.1.1",
          "snmpEnabled": true,
          "snmpCommunityRo": "publicRO",
          "snmpServer": "192.168.1.50",
          "syslogServer": "192.168.1.60",
          "syslogLevel": "informational",
          "vlans": [
            { "id": 10, "name": "USERS" },
            { "id": 20, "name": "SERVERS" }
          ],
          "interfaces": [
            { "name": "GigabitEthernet0/1", "description": "to-PC", "mode": "access",
              "vlanId": 10, "portfast": true, "bpduGuard": true, "portSecurity": true },
            { "name": "GigabitEthernet0/24", "description": "uplink", "mode": "trunk",
              "nativeVlan": 99, "allowedVlans": "10,20", "dhcpTrusted": true }
          ],
          "sviInterfaces": [
            { "vlan": 10, "ipAddress": "10.0.10.1", "subnetMask": "255.255.255.0", "description": "users-gw" }
          ],
          "defaultGateway": "10.0.0.254",
          "staticRoutes": [
            { "network": "10.1.0.0", "mask": "255.255.0.0", "nextHop": "10.0.0.254" }
          ],
          "saveToMemory": true
        }
        """;

    @Test
    void generatesValidIosCliFromSampleConfig() throws Exception {
        JsonNode config = mapper.readTree(SAMPLE);

        String cli = generator.generateCommand(config);

        System.out.println("\n----- Сгенерированный Cisco IOS CLI -----\n" + cli + "\n-----------------------------------------\n");

        // Каркас конфигурационной сессии
        assertThat(cli).startsWith("configure terminal");
        assertThat(cli).endsWith("write memory\n");          // saveToMemory: true
        assertThat(cli).contains("end\n");

        // Система
        assertThat(cli).contains("hostname sw-core-01");
        assertThat(cli).contains("ip domain-name lab.local");
        assertThat(cli).contains("enable secret S3cr3t!");
        assertThat(cli).contains("service password-encryption");

        // SSH / VTY
        assertThat(cli).contains("ip ssh version 2");
        assertThat(cli).contains("crypto key generate rsa modulus 2048"); // т.к. задан domainName
        assertThat(cli).contains("line vty 0 15");
        assertThat(cli).contains("transport input ssh");

        // VLAN
        assertThat(cli).contains("vlan 10").contains("name USERS");
        assertThat(cli).contains("vlan 20").contains("name SERVERS");

        // Access-порт с защитами
        assertThat(cli).contains("interface GigabitEthernet0/1");
        assertThat(cli).contains("switchport mode access");
        assertThat(cli).contains("switchport access vlan 10");
        assertThat(cli).contains("spanning-tree portfast");
        assertThat(cli).contains("spanning-tree bpduguard enable");
        assertThat(cli).contains("switchport port-security");

        // Trunk-аплинк
        assertThat(cli).contains("interface GigabitEthernet0/24");
        assertThat(cli).contains("switchport mode trunk");
        assertThat(cli).contains("switchport trunk native vlan 99");
        assertThat(cli).contains("switchport trunk allowed vlan 10,20");
        assertThat(cli).contains("ip dhcp snooping trust");

        // SVI
        assertThat(cli).contains("interface Vlan10");
        assertThat(cli).contains("ip address 10.0.10.1 255.255.255.0");

        // NTP / SNMP / Syslog / маршрутизация
        assertThat(cli).contains("ntp server 192.168.1.1");
        assertThat(cli).contains("snmp-server community publicRO RO");
        assertThat(cli).contains("logging host 192.168.1.60");
        assertThat(cli).contains("ip default-gateway 10.0.0.254");
        assertThat(cli).contains("ip route 10.1.0.0 255.255.0.0 10.0.0.254");
    }

    @Test
    void omitsWriteMemoryWhenNotPersisting() throws Exception {
        JsonNode config = mapper.readTree("{ \"hostname\": \"tmp\", \"saveToMemory\": false }");

        String cli = generator.generateCommand(config);

        assertThat(cli).contains("hostname tmp");
        assertThat(cli).doesNotContain("write memory");
    }

    @Test
    void accessPortDoesNotGetTrunkCommands() throws Exception {
        JsonNode config = mapper.readTree("""
            { "interfaces": [ { "name": "Gi0/5", "mode": "access", "vlanId": 30 } ] }
            """);

        String cli = generator.generateCommand(config);

        assertThat(cli).contains("interface Gi0/5");
        assertThat(cli).contains("switchport access vlan 30");
        assertThat(cli).doesNotContain("switchport mode trunk");
    }
}
