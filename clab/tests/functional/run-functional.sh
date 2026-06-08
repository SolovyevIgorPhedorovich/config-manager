#!/usr/bin/env bash
# Функциональный тест config-manager поверх стенда functional.clab.yml.
#
# Проверяет сквозной путь:
#   1. логин (JWT)
#   2. запуск сканирования сети /24 (SNMP + SSH)
#   3. ожидание завершения, опознание всех типов устройств
#   4. инвентаризация одного устройства
#   5. применение конфигурации на Linux-устройство по SSH
#
# Предусловия:
#   - подняты deps (postgres+redis) и бэкенд на :8080
#   - применён seed/01-seed-admin.sql
#   - развёрнут functional.clab.yml
#
#   ./run-functional.sh
set -euo pipefail
cd "$(dirname "$0")"
source ../lib.sh

FAILED=0
echo "== 1. Логин =="
TOKEN="$(login)"
[ -n "$TOKEN" ] && [ "$TOKEN" != "null" ] && pass "получен JWT" || { fail "логин не удался"; exit 1; }

echo "== 2. Запуск сканирования ${SCAN_SUBNET}/${SCAN_MASK} =="
SCAN_QS="ipaddr=${SCAN_SUBNET}&mask=${SCAN_MASK}&community=${SNMP_COMMUNITY}&snmpv=v2c&scanMode=all"
SCAN_QS="${SCAN_QS}&sshUsername=${SSH_USER}&sshPassword=${SSH_PASS}"
START=$(api GET "/api/v1/devices/scan?${SCAN_QS}")
TASK_ID=$(echo "$START" | jq -r '.taskId')
[ -n "$TASK_ID" ] && [ "$TASK_ID" != "null" ] && pass "scan taskId=$TASK_ID" || { fail "scan не запустился: $START"; exit 1; }

echo "== 3. Ожидание завершения скана =="
STATUS=""; RESULT=""
for i in $(seq 1 60); do
  RESULT=$(api GET "/api/v1/devices/scan/status?taskId=${TASK_ID}")
  STATUS=$(echo "$RESULT" | jq -r '.status')
  if [ "$STATUS" = "completed" ]; then break; fi
  printf '\r  ... %s (%s)        ' "$STATUS" "$(echo "$RESULT" | jq -r '.message // empty')"
  sleep 3
done
echo
[ "$STATUS" = "completed" ] && pass "скан завершён" || { fail "скан не завершился: $RESULT"; exit 1; }

COUNT=$(echo "$RESULT" | jq -r '.count')
echo "  найдено устройств: $COUNT"
echo "$RESULT" | jq -r '.results[] | "    \(.device.ips[0] // "?")  type=\(.device.typeCode)  \(.device.hostname)  [\(.scanStatus)]"' || true

# Проверяем, что опознаны все ожидаемые типы (0=PC,1=МФУ,2=CISCO,3=PROXMOX)
have_type() { echo "$RESULT" | jq -e --argjson t "$1" '[.results[].device.typeCode] | index($t) != null' >/dev/null; }
have_type 2 && pass "опознан Cisco"   || fail "Cisco не опознан"
have_type 0 && pass "опознан PC"      || fail "PC не опознан"
have_type 1 && pass "опознан МФУ"     || fail "МФУ не опознан"
have_type 3 && pass "опознан Proxmox" || fail "Proxmox не опознан"

echo "== 4. Инвентаризация одного устройства =="
DEV_ID=$(echo "$RESULT" | jq -r '.results[0].device.id')
INV=$(api POST "/api/v1/devices/${DEV_ID}/inventory" '{"community":"public","snmpVersion":"v2c"}')
echo "$INV" | jq -e '.success == true' >/dev/null && pass "инвентаризация id=$DEV_ID (method=$(echo "$INV" | jq -r .detectionMethod))" || fail "инвентаризация: $INV"

echo "== 5. Применение конфига на Linux по SSH =="
# Выбираем Linux-устройство (type=0 PC), у которого открыт SSH-стенд
LINUX_ID=$(echo "$RESULT" | jq -r '[.results[].device | select(.typeCode==0)][0].id')
if [ -n "$LINUX_ID" ] && [ "$LINUX_ID" != "null" ]; then
  CFG=$(cat <<JSON
{
  "deviceIds": [${LINUX_ID}],
  "configData": { "hostname": "cfgmgr-test", "saveToMemory": false,
                  "sshAuthorizedKey": "ssh-ed25519 AAAATESTKEY cfgmgr@test" },
  "credentials": { "${LINUX_ID}": { "port": 22, "username": "${SSH_USER}", "password": "${SSH_PASS}" } }
}
JSON
)
  APPLY=$(api POST "/api/v1/devices/configure" "$CFG")
  GROUP_ID=$(echo "$APPLY" | jq -r '.groupTaskIds[0] // empty')
  [ -n "$GROUP_ID" ] && pass "apply принят, groupTaskId=$GROUP_ID" || fail "apply не принят: $APPLY"

  if [ -n "$GROUP_ID" ]; then
    for i in $(seq 1 20); do
      EXEC=$(api GET "/api/commands/status/${GROUP_ID}" || true)
      ST=$(echo "$EXEC" | jq -r '.status // empty' 2>/dev/null || true)
      [ "$ST" = "COMPLETED" ] || [ "$ST" = "FAILED" ] && break
      sleep 2
    done
    echo "  статус выполнения команды: ${ST:-unknown}"
  fi
else
  fail "не найдено Linux-устройство для apply"
fi

echo
if [ "$FAILED" -eq 0 ]; then echo "ИТОГ: всё прошло ✅"; else echo "ИТОГ: провалов — $FAILED ❌"; exit 1; fi
