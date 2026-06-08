# Тестовый стенд config-manager на containerlab

Набор ресурсов для **функционального** и **нагрузочного** тестирования бэкенда
config-manager против эмулированной сети устройств.

Бэкенд опознаёт устройства по `SNMP → WinRM → SSH → открытым портам` и определяет
тип (Cisco / PC-Linux / PC-Windows / МФУ / Proxmox) по `sysDescr`. Поэтому
устройства эмулируются **лёгкими Linux-контейнерами** с настроенным `snmpd`
(подменённый `sysDescr`/`sysName`) и `sshd` — настоящие образы вендоров не нужны.
Один образ `device-sim` изображает любой тип через переменную `DEVICE_TYPE`.

```
clab/
├── device-sim/            Образ симулятора устройства (snmpd + sshd)
│   ├── Dockerfile
│   ├── entrypoint.sh       Настройка SNMP/SSH по DEVICE_TYPE
│   └── fake-ios.sh         Эмуляция Cisco IOS CLI для apply-конфига
├── topologies/
│   ├── functional.clab.yml      Стенд: по 1–2 устройства каждого типа
│   └── gen-load-topology.sh     Генератор стенда на N устройств (по умолч. /24)
├── deps/docker-compose.yml      PostgreSQL + Redis для бэкенда
├── seed/01-seed-admin.sql       Тестовый admin/admin123 для API
└── tests/
    ├── lib.sh
    ├── functional/run-functional.sh   Сквозной функциональный тест (curl+jq)
    └── load/                            Нагрузочные сценарии k6
        ├── k6-scan.js       Полный цикл сканирования /24
        ├── k6-api-mix.js    Смешанная нагрузка на REST API
        └── run-load.sh
```

## Что эмулируется

| DEVICE_TYPE | Опознаётся как | Канал детекта | sysDescr (по умолч.)            | apply-конфига |
|-------------|----------------|---------------|---------------------------------|---------------|
| `cisco`     | CISCO          | SNMP          | `Cisco IOS Software ... C2960X`  | SSH → fake-ios (success) |
| `linux`     | PC (Linux)     | SNMP / SSH    | `Linux ... Ubuntu x86_64`        | SSH → реальный bash |
| `windows`   | PC (Windows)   | SNMP          | `... Windows Version 10.0`       | WinRM — не эмулируется |
| `mfu`       | МФУ            | SNMP          | `KYOCERA ECOSYS M2640idw`        | SNMP — не эмулируется |
| `proxmox`   | PROXMOX        | SNMP / SSH    | `Proxmox VE 8.1 (kernel ...pve)` | SSH → реальный bash |

Доступы стенда: SNMP community `public` (v2c), SSH `netadmin / netadmin123`
(и `root / root123`).

> **Ограничения эмуляции.** WinRM и SNMP-set реальных МФУ не поднимаются —
> Windows/МФУ покрыты только на этапе обнаружения. Apply для Cisco отдаёт
> успешный ответ через `fake-ios.sh`, но физически конфиг не меняет. Для Linux и
> Proxmox apply выполняется по-настоящему (bash по SSH).

## Предварительные требования

- Docker + [containerlab](https://containerlab.dev/install/)
- `k6` для нагрузки, `jq` и `curl` для функционального теста
- JDK 21 для запуска бэкенда (`backend/`)

## Запуск: пошагово

### 1. Зависимости и бэкенд

```bash
# Postgres + Redis
docker compose -f clab/deps/docker-compose.yml up -d

# Бэкенд (создаёт схему на старте)
cd backend && ./gradlew bootRun
```

### 2. Тестовый администратор

```bash
psql "postgresql://igor:8008@localhost:5432/mydatabase" -f clab/seed/01-seed-admin.sql
# логин admin / admin123
```

### 3. Стенд устройств

```bash
docker build -t configmanager/device-sim:latest clab/device-sim

# Функциональный стенд (≈7 устройств всех типов)
sudo containerlab deploy -t clab/topologies/functional.clab.yml

# ИЛИ нагрузочный стенд на полный /24 (~250 устройств)
clab/topologies/gen-load-topology.sh 250 > clab/topologies/load.clab.yml
sudo containerlab deploy -t clab/topologies/load.clab.yml
```

Все устройства попадают в сеть `172.100.100.0/24`, которую сканирует бэкенд
(совпадает с `network-scan.max-hosts: 256` и ограничением маски `≤ /24`).
Бэкенд на хосте видит этот bridge напрямую (ping/SNMP/SSH работают).

### 4. Функциональный тест

```bash
clab/tests/functional/run-functional.sh
```
Проверяет: логин → запуск скана `/24` → завершение → опознание всех типов →
инвентаризацию устройства → применение конфига на Linux по SSH.

### 5. Нагрузочный тест

```bash
cd clab/tests/load
./run-load.sh api      # смешанная нагрузка на API (ramp 0→50→100 VU)
./run-load.sh scan     # полный цикл сканирования /24, метрика scan_duration_ms
./run-load.sh all
```
Сводки сохраняются в `clab/tests/load/results/`.

Параметры через `-e` (или env): `API`, `VUS`, `ITERATIONS`, `VUS_MAX`,
`SUBNET`, `COMMUNITY`, `SSH_USER`, `SSH_PASS`. Пример:
```bash
k6 run -e API=http://localhost:8080 -e VUS=3 -e ITERATIONS=10 k6-scan.js
```

## Подгонка масштаба

Полный `/24` — это ~250 контейнеров; на ноутбуке тяжело. Уменьшите число:
```bash
clab/topologies/gen-load-topology.sh 40 > clab/topologies/load.clab.yml
```
и сканируйте подсеть поменьше, например `-e SUBNET=172.100.100.0 -e MASK=26`
(бэкенд принимает маску `≤ /24`).

## Уборка

```bash
sudo containerlab destroy -t clab/topologies/functional.clab.yml   # или load.clab.yml
docker compose -f clab/deps/docker-compose.yml down -v
```

## Диагностика

| Симптом | Причина / решение |
|---------|-------------------|
| Скан находит 0 устройств | Бэкенд не видит bridge `clab-cfgmgr`. Проверь `ping 172.100.100.11` с хоста. |
| Устройство не опознано по SNMP | `snmpget -v2c -c public 172.100.100.11 sysDescr.0` должен вернуть нужный текст. |
| Proxmox опознан как PC | В `sysDescr` не должно быть слова `Linux` (см. `entrypoint.sh`). |
| Логин 401 | Не применён `seed/01-seed-admin.sql` или бэкенд не пересоздал схему. |
| Cisco apply «успешен», но конфиг не изменился | Так и задумано: `fake-ios.sh` лишь подтверждает приём. |
