#!/bin/bash
# Настраивает snmpd и sshd под нужный тип устройства, затем запускает их.
set -e

TYPE="${DEVICE_TYPE:-linux}"
NAME="${SYS_NAME:-$(hostname)}"

# ── sysDescr / sysObjectID по типу устройства ──────────────────────────────
# Значения подобраны так, чтобы NetworkScannerService.detectDeviceType()
# классифицировал устройство в нужный DeviceType.
case "$TYPE" in
  cisco)
    DESCR_DEFAULT="Cisco IOS Software, C2960X Software (C2960X-UNIVERSALK9-M), Version 15.2(7)E3, RELEASE SOFTWARE (fc3)"
    OBJID=".1.3.6.1.4.1.9.1.2134"
    ;;
  windows)
    DESCR_DEFAULT="Hardware: Intel64 Family 6 Model 158 - Software: Windows Version 10.0 (Build 19045 Multiprocessor Free)"
    OBJID=".1.3.6.1.4.1.311.1.1.3.1.1"
    ;;
  mfu|printer)
    DESCR_DEFAULT="KYOCERA ECOSYS M2640idw; firmware 2VG_2000.004.001"
    OBJID=".1.3.6.1.4.1.1347.43"
    ;;
  proxmox|vm)
    # ВАЖНО: без слова "Linux" — иначе detectDeviceType вернёт PC, а не PROXMOX
    # (проверка на linux/ubuntu/debian идёт раньше проверки на proxmox).
    DESCR_DEFAULT="Proxmox VE 8.1 (kernel 6.5.11-7-pve)"
    OBJID=".1.3.6.1.4.1.8072.3.2.10"
    ;;
  *)  # linux / generic PC
    TYPE="linux"
    DESCR_DEFAULT="Linux ${NAME} 5.15.0-91-generic #101-Ubuntu SMP x86_64 GNU/Linux"
    OBJID=".1.3.6.1.4.1.8072.3.2.10"
    ;;
esac

DESCR="${SYS_DESCR:-$DESCR_DEFAULT}"

# ── SNMP ───────────────────────────────────────────────────────────────────
if [ "${ENABLE_SNMP:-true}" = "true" ]; then
  cat > /etc/snmp/snmpd.conf <<EOF
agentAddress udp:161
rocommunity ${SNMP_COMMUNITY:-public}
# Подменяем стандартные OID system-группы под изображаемое устройство
override .1.3.6.1.2.1.1.1.0 octet_str "${DESCR}"
override .1.3.6.1.2.1.1.2.0 object_id ${OBJID}
override .1.3.6.1.2.1.1.4.0 octet_str "ConfigManager Test Lab"
override .1.3.6.1.2.1.1.5.0 octet_str "${NAME}"
override .1.3.6.1.2.1.1.6.0 octet_str "containerlab"
EOF
fi

# ── SSH ────────────────────────────────────────────────────────────────────
if [ "${ENABLE_SSH:-true}" = "true" ]; then
  ssh-keygen -A >/dev/null 2>&1 || true
  sed -i 's/^#\?PermitRootLogin.*/PermitRootLogin yes/'            /etc/ssh/sshd_config
  sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication yes/' /etc/ssh/sshd_config
  echo "root:${ROOT_PASSWORD:-root123}" | chpasswd

  SU="${SSH_USER:-netadmin}"
  id "$SU" &>/dev/null || useradd -m -s /bin/bash "$SU"
  echo "${SU}:${SSH_PASSWORD:-netadmin123}" | chpasswd

  # Для Cisco отдаём правдоподобный IOS-вывод на любую exec-команду,
  # чтобы apply конфигурации Cisco завершался успехом в тесте.
  if [ "$TYPE" = "cisco" ]; then
    printf '\nMatch User %s\n    ForceCommand /usr/local/bin/fake-ios.sh\n' "$SU" >> /etc/ssh/sshd_config
  fi
fi

# ── Запуск (один процесс держим на переднем плане) ─────────────────────────
if [ "${ENABLE_SNMP:-true}" = "true" ] && [ "${ENABLE_SSH:-true}" != "true" ]; then
  exec snmpd -f -Lo -C -c /etc/snmp/snmpd.conf
fi

if [ "${ENABLE_SNMP:-true}" = "true" ]; then
  snmpd -Lf /dev/null -C -c /etc/snmp/snmpd.conf   # фоновый демон
fi
exec /usr/sbin/sshd -D -e
