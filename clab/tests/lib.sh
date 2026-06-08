#!/usr/bin/env bash
# Общие helper-функции для тест-скриптов config-manager.
set -euo pipefail

API="${API:-http://localhost:8080}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-admin123}"

# SNMP / SSH параметры стенда (см. device-sim/entrypoint.sh)
SNMP_COMMUNITY="${SNMP_COMMUNITY:-public}"
SSH_USER="${SSH_USER:-netadmin}"
SSH_PASS="${SSH_PASS:-netadmin123}"
SCAN_SUBNET="${SCAN_SUBNET:-172.100.100.0}"
SCAN_MASK="${SCAN_MASK:-24}"

require() { command -v "$1" >/dev/null || { echo "Нужна утилита: $1" >&2; exit 1; }; }
require curl
require jq

# login -> печатает JWT-токен
login() {
  local resp
  resp=$(curl -fsS -X POST "$API/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}")
  echo "$resp" | jq -r '.token'
}

# api METHOD PATH [json-body]  — авторизованный запрос, печатает тело ответа
api() {
  local method="$1" path="$2" body="${3:-}"
  if [ -n "$body" ]; then
    curl -fsS -X "$method" "$API$path" \
      -H "Authorization: Bearer $TOKEN" \
      -H 'Content-Type: application/json' -d "$body"
  else
    curl -fsS -X "$method" "$API$path" -H "Authorization: Bearer $TOKEN"
  fi
}

pass() { echo "  ✅ $*"; }
fail() { echo "  ❌ $*"; FAILED=$((FAILED+1)); }
