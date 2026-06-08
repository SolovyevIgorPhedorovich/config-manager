#!/bin/bash
# Минимальная эмуляция Cisco IOS CLI. Бэкенд (SSHAdapter) шлёт весь набор команд
# одной exec-командой — она приходит в $SSH_ORIGINAL_COMMAND. Возвращаем
# правдоподобный вывод и код 0, чтобы probe/apply Cisco отрабатывал.
cmd="${SSH_ORIGINAL_COMMAND:-}"

case "$cmd" in
  *"show version"*)
    echo "Cisco IOS Software, C2960X Software (C2960X-UNIVERSALK9-M), Version 15.2(7)E3, RELEASE SOFTWARE (fc3)"
    echo "cisco WS-C2960X-48FPD-L (APM86XXX) processor (revision K0)"
    echo "Model Number: WS-C2960X-48FPD-L"
    ;;
  *"running-config"*)
    echo "hostname ${HOSTNAME}"
    ;;
  *)
    # configure terminal / interface / vlan / write memory ... — подтверждаем приём
    echo "Configuration accepted"
    ;;
esac
exit 0
