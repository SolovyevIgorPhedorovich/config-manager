#!/usr/bin/env bash
# Генерация self-signed TLS-сертификата для локальной разработки.
# Keystore не коммитится (см. .gitignore) — запустите этот скрипт один раз
# перед первым стартом бэкенда с включённым HTTPS (SSL_ENABLED=true, по умолчанию).
#
# В проде используйте реальный сертификат и переопределите:
#   SSL_KEYSTORE=/path/to/keystore.p12 SSL_KEYSTORE_PASSWORD=... SSL_KEY_ALIAS=...
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="${1:-$DIR/src/main/resources/keystore.p12}"
PASS="${SSL_KEYSTORE_PASSWORD:-changeit}"
ALIAS="${SSL_KEY_ALIAS:-configmanager}"

keytool -genkeypair \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 2048 -validity 3650 \
  -storetype PKCS12 \
  -keystore "$OUT" \
  -storepass "$PASS" \
  -dname "CN=localhost, OU=Dev, O=ConfigManager, C=RU" \
  -ext "SAN=dns:localhost,ip:127.0.0.1"

echo "Keystore создан: $OUT (alias=$ALIAS)"
