#!/usr/bin/env bash
# Запуск нагрузочных сценариев k6. Требует установленного k6 (https://k6.io).
#
#   ./run-load.sh scan      # полный цикл сканирования /24
#   ./run-load.sh api       # смешанная нагрузка на REST API
#   ./run-load.sh all       # оба (api, затем scan)
set -euo pipefail
cd "$(dirname "$0")"

command -v k6 >/dev/null || { echo "k6 не установлен: https://grafana.com/docs/k6/latest/set-up/install-k6/" >&2; exit 1; }

API="${API:-http://localhost:8080}"
export API
OUT_DIR="${OUT_DIR:-./results}"
mkdir -p "$OUT_DIR"
TS=$(date +%Y%m%d-%H%M%S)

run() {
  local name="$1" file="$2"
  echo "== k6: $name =="
  k6 run --summary-export "$OUT_DIR/${name}-${TS}.json" "$file"
}

case "${1:-all}" in
  scan) run scan k6-scan.js ;;
  api)  run api  k6-api-mix.js ;;
  all)  run api k6-api-mix.js; run scan k6-scan.js ;;
  *)    echo "Использование: $0 {scan|api|all}" >&2; exit 1 ;;
esac

echo "Результаты: $OUT_DIR/"
