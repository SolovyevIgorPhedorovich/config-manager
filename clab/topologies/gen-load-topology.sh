#!/usr/bin/env bash
# Генерирует containerlab-топологию из N симулированных устройств для
# нагрузочного теста. По умолчанию заполняет весь /24 (172.100.100.2 .. .251)
# смесью типов устройств в реалистичной пропорции.
#
#   ./gen-load-topology.sh [N] > load.clab.yml
#   N — число устройств (по умолчанию 250, максимум 250 для /24).
#
# Затем:
#   docker build -t configmanager/device-sim:latest ../device-sim
#   sudo containerlab deploy -t load.clab.yml
set -euo pipefail

N="${1:-250}"
SUBNET_PREFIX="172.100.100"
FIRST_HOST=2          # .0=сеть, .1=шлюз bridge
MAX_HOST=251          # .255=broadcast; оставляем запас
NAME="${TOPO_NAME:-configmanager-load}"

if (( N < 1 || N > (MAX_HOST - FIRST_HOST + 1) )); then
  echo "N должно быть в диапазоне 1..$((MAX_HOST - FIRST_HOST + 1))" >&2
  exit 1
fi

# Пропорция типов (на каждые 20 устройств): 9 linux, 6 windows, 2 cisco, 2 mfu, 1 proxmox
type_for() {
  case $(( $1 % 20 )) in
    0|1|2|3|4|5|6|7|8)      echo "linux"   ;;
    9|10|11|12|13|14)       echo "windows" ;;
    15|16)                  echo "cisco"   ;;
    17|18)                  echo "mfu"     ;;
    19)                     echo "proxmox" ;;
  esac
}

cat <<EOF
# СГЕНЕРИРОВАНО gen-load-topology.sh — нагрузочный стенд на ${N} устройств.
# Бэкенд сканирует ${SUBNET_PREFIX}.0/24.
name: ${NAME}

mgmt:
  network: clab-cfgmgr
  ipv4-subnet: ${SUBNET_PREFIX}.0/24

topology:
  defaults:
    kind: linux
    image: configmanager/device-sim:latest
  nodes:
EOF

for (( i=0; i<N; i++ )); do
  host=$(( FIRST_HOST + i ))
  type=$(type_for "$i")
  printf -v idx '%03d' "$host"
  node="dev-${type}-${idx}"
  extra=""
  # У windows и mfu в этом стенде SSH не нужен — экономим ресурсы
  case "$type" in
    windows|mfu) extra=", ENABLE_SSH: \"false\"" ;;
  esac
  cat <<EOF
    ${node}:
      mgmt-ipv4: ${SUBNET_PREFIX}.${host}
      env: { DEVICE_TYPE: ${type}, SYS_NAME: ${node}${extra} }
EOF
done
