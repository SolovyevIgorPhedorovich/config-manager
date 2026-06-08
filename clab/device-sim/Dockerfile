# Универсальный симулятор сетевого устройства для нагрузочного/функционального
# тестирования config-manager. Один образ изображает Cisco / Linux / Windows /
# МФУ / Proxmox — тип задаётся переменной окружения DEVICE_TYPE.
#
# Бэкенд опознаёт устройство по SNMP (sysDescr/sysName) → WinRM → SSH → портам,
# поэтому реальные образы вендоров не нужны: достаточно snmpd с подменённым
# sysDescr и обычного sshd.
FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
        snmpd snmp \
        openssh-server \
        passwd \
        iproute2 iputils-ping \
        procps bash \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /run/sshd /var/lib/snmp

COPY entrypoint.sh /usr/local/bin/entrypoint.sh
COPY fake-ios.sh   /usr/local/bin/fake-ios.sh
RUN chmod +x /usr/local/bin/entrypoint.sh /usr/local/bin/fake-ios.sh

# SNMP-агент (UDP/161) и SSH (TCP/22)
EXPOSE 161/udp 22/tcp

ENV DEVICE_TYPE=linux \
    SYS_NAME="" \
    SYS_DESCR="" \
    SNMP_COMMUNITY=public \
    SSH_USER=netadmin \
    SSH_PASSWORD=netadmin123 \
    ROOT_PASSWORD=root123 \
    ENABLE_SNMP=true \
    ENABLE_SSH=true

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
