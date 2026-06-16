import React, { useEffect, useMemo, useState } from 'react';
import {
  Card, Table, Tag, Typography, Row, Col, Statistic,
  Empty, message, Button, Alert
} from 'antd';
import { WarningOutlined } from '@ant-design/icons';
import { getErrorMessage } from '../utils/errorMessage';
import { Pie, Line } from 'react-chartjs-2';
import {
  Chart as ChartJS,
  ArcElement,
  Tooltip,
  Legend,
  CategoryScale,
  LinearScale,
  Title as ChartTitle,
  BarController,
  LineController,
  PointElement,
  LineElement
} from 'chart.js';

import { devicesApi } from '../api/devicesApi';
import { eventApi } from '../api/eventApi';
import ResolveDriftModal from '../components/ResolveDriftModal';
import type { Device, EventLog } from '../types';

const { Title, Text } = Typography;

ChartJS.register(
  ArcElement,
  Tooltip,
  Legend,
  CategoryScale,
  LinearScale,
  ChartTitle,
  BarController,
  LineController,
  PointElement,
  LineElement
);

const dashboardCategories = [
  { key: 'windows', name: 'Windows', color: '#1890ff' },
  { key: 'linux', name: 'Linux', color: '#722ed1' },
  { key: 'mfu', name: 'МФУ', color: '#52c41a' },
  { key: 'cisco', name: 'Cisco', color: '#fa8b0f' },
  { key: 'proxmox', name: 'Proxmox', color: '#f53f3f' },
] as const;

const getDeviceCategory = (device: Device) => {
  const type = (device.type || '').toLowerCase();
  const osVersion = (device.osVersion || '').toLowerCase();

  if (device.typeCode === 1 || type.includes('мфу')) return 'mfu';
  if (device.typeCode === 2 || type.includes('cisco')) return 'cisco';
  if (device.typeCode === 3 || type.includes('vm') || osVersion.includes('proxmox')) return 'proxmox';
  if (osVersion.includes('linux') || osVersion.includes('ubuntu') || osVersion.includes('debian')) return 'linux';
  return 'windows';
};

// ── Классификация событий журнала ───────────────────────────────────────────
// «Изменения» — события, меняющие инвентарь/конфигурацию устройств.
const CHANGE_EVENTS = new Set([
  'DEVICE_ADDED', 'DEVICE_DELETED', 'DEVICE_UPDATED',
  'CONFIG_APPLIED', 'CONFIG_ROLLED_BACK',
  'CONFIG_APPLY_STARTED', 'CONFIG_APPLY_SCHEDULED',
  'DRIFT_REAPPLY',
]);

// Ошибки — провалившиеся операции, но без событий входа (LOGIN_*): неудачные
// логины относятся к аудиту безопасности, а не к динамике изменений конфигов.
const isErrorEvent = (eventType?: string) =>
  !!eventType && !eventType.startsWith('LOGIN_') && /(FAIL|FAILED|FAILURE|ERROR)$/.test(eventType);

const isChangeEvent = (eventType?: string) =>
  !!eventType && CHANGE_EVENTS.has(eventType);

// Человекочитаемые подписи типов событий.
const EVENT_LABELS: Record<string, string> = {
  DEVICE_ADDED: 'Устройство добавлено',
  DEVICE_DELETED: 'Устройство удалено',
  DEVICE_UPDATED: 'Устройство изменено',
  CONFIG_APPLIED: 'Конфигурация применена',
  CONFIG_ROLLED_BACK: 'Откат конфигурации',
  CONFIG_APPLY_STARTED: 'Запуск применения конфигурации',
  CONFIG_APPLY_SCHEDULED: 'Применение запланировано',
  DRIFT_REAPPLY: 'Разрешение расхождения',
  LOGIN_SUCCESS: 'Успешный вход',
  LOGIN_FAILURE: 'Неудачный вход',
};
const eventLabel = (eventType?: string) =>
  (eventType && EVENT_LABELS[eventType]) || eventType || '—';

const dateKey = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

const formatDateTime = (iso?: string) =>
  iso ? new Date(iso).toLocaleString('ru-RU') : '—';

export default function DashboardPage() {
  const [devices, setDevices] = useState<Device[]>([]);
  const [events, setEvents] = useState<EventLog[]>([]);
  const [eventsAvailable, setEventsAvailable] = useState(true);
  const [loading, setLoading] = useState(true);
  // Устройство, для которого открыта модалка разрешения конфликта (drift).
  const [driftDevice, setDriftDevice] = useState<Device | null>(null);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      const devicesRes = await devicesApi.getAll();
      setDevices(devicesRes.data);
    } catch (err) {
      setDevices([]);
      console.error('Ошибка загрузки устройств:', err);
      message.error(getErrorMessage(err, 'Не удалось загрузить устройства'));
    }

    // Журнал событий доступен только ADMIN/AUDITOR — для остальных это 403,
    // и дашборд не должен падать целиком: просто прячем блоки на основе событий.
    try {
      const logsRes = await eventApi.getLogs({ size: 500 });
      setEvents(logsRes.data);
      setEventsAvailable(true);
    } catch (err: any) {
      setEvents([]);
      setEventsAvailable(false);
      if (err?.response?.status && err.response.status !== 403) {
        console.error('Ошибка загрузки журнала событий:', err);
      }
    } finally {
      setLoading(false);
    }
  };

  const chartData = useMemo(() =>
    dashboardCategories.map((category) => ({
      typeKey: category.key,
      name: category.name,
      count: devices.filter((device) => getDeviceCategory(device) === category.key).length,
      color: category.color,
    })),
    [devices]
  );

  const pieData = {
    labels: chartData.map(d => d.name),
    datasets: [
      {
        data: chartData.map(d => d.count),
        backgroundColor: chartData.map(d => d.color),
        hoverBackgroundColor: chartData.map(d => d.color),
      },
    ],
  };

  // ── Динамика за последние 7 дней: изменения и ошибки по дням ──────────────
  const dynamics = useMemo(() => {
    const days: { key: string; label: string }[] = [];
    const today = new Date();
    for (let i = 6; i >= 0; i--) {
      const d = new Date(today);
      d.setDate(today.getDate() - i);
      days.push({
        key: dateKey(d),
        label: d.toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit' }),
      });
    }
    const changes = new Map(days.map(d => [d.key, 0]));
    const errors = new Map(days.map(d => [d.key, 0]));

    for (const e of events) {
      if (!e.createdAt) continue;
      const key = dateKey(new Date(e.createdAt));
      if (isErrorEvent(e.eventType)) {
        if (errors.has(key)) errors.set(key, errors.get(key)! + 1);
      } else if (isChangeEvent(e.eventType)) {
        if (changes.has(key)) changes.set(key, changes.get(key)! + 1);
      }
    }
    return {
      labels: days.map(d => d.label),
      changes: days.map(d => changes.get(d.key) || 0),
      errors: days.map(d => errors.get(d.key) || 0),
    };
  }, [events]);

  const lineData = {
    labels: dynamics.labels,
    datasets: [
      {
        label: 'Изменения',
        data: dynamics.changes,
        borderColor: '#1890ff',
        backgroundColor: 'rgba(24,144,255,0.15)',
        tension: 0.3,
        fill: true,
      },
      {
        label: 'Ошибки',
        data: dynamics.errors,
        borderColor: '#f5222d',
        backgroundColor: 'rgba(245,34,45,0.15)',
        tension: 0.3,
        fill: true,
      },
    ],
  };

  // ── Конфликты конфигураций (устройство ↔ база) ────────────────────────────
  const driftDevices = useMemo(
    () => devices.filter(d => d.configDrift),
    [devices]
  );

  // ── Последние ошибки из журнала ───────────────────────────────────────────
  const errorEvents = useMemo(
    () => events
      .filter(e => isErrorEvent(e.eventType))
      .sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || '')),
    [events]
  );

  const errorCount7d = useMemo(
    () => dynamics.errors.reduce((a, b) => a + b, 0),
    [dynamics]
  );

  const driftColumns = [
    { title: 'Устройство', dataIndex: 'hostname', key: 'hostname' },
    {
      title: 'IP',
      key: 'ip',
      render: (_: any, d: Device) => <Text type="secondary">{d.ips?.[0] || '—'}</Text>,
    },
    { title: 'Тип', dataIndex: 'type', key: 'type' },
    {
      title: 'Статус',
      key: 'status',
      render: () => <Tag color="warning" icon={<WarningOutlined />}>Конфликт (drift)</Tag>,
    },
    {
      title: 'Действие',
      key: 'action',
      render: (_: any, d: Device) => (
        <Button type="link" onClick={() => setDriftDevice(d)}>Разрешить</Button>
      ),
    },
  ];

  const errorColumns = [
    {
      title: 'Время',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (v: string) => formatDateTime(v),
    },
    {
      title: 'Событие',
      dataIndex: 'eventType',
      key: 'eventType',
      render: (v: string) => <Tag color="error">{eventLabel(v)}</Tag>,
    },
    {
      title: 'Источник',
      key: 'source',
      render: (_: any, e: EventLog) =>
        e.userName
          ? e.userName
          : e.aggregateType
            ? `${e.aggregateType}${e.aggregateId != null ? ` #${e.aggregateId}` : ''}`
            : '—',
    },
  ];

  const totalDevices = devices.length;
  const pcCount = devices.filter((device) => device.typeCode === 0 || device.type === 'ПК').length;
  const linuxCount = chartData.find(d => d.typeKey === 'linux')?.count || 0;
  const windowsCount = chartData.find(d => d.typeKey === 'windows')?.count || 0;
  const mfuCount = chartData.find(d => d.typeKey === 'mfu')?.count || 0;
  const proxmoxCount = chartData.find(d => d.typeKey === 'proxmox')?.count || 0;

  return (
    <div style={{ padding: '24px' }}>
      <Title level={2}>📊 Панель управления</Title>
      <Text type="secondary">Общая информация о состоянии ИТ-инфраструктуры</Text>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 40 }}>
          Загрузка данных...
        </div>
      ) : (
        <>
          <Row gutter={[16, 16]} style={{ marginTop: 16, marginBottom: 24 }}>
            <Col xs={24} sm={12} md={8} xl={4}>
              <Card><Statistic title="Всего" value={totalDevices} /></Card>
            </Col>
            <Col xs={24} sm={12} md={8} xl={4}>
              <Card><Statistic title="ПК" value={pcCount} /></Card>
            </Col>
            <Col xs={24} sm={12} md={8} xl={4}>
              <Card><Statistic title="Linux" value={linuxCount} /></Card>
            </Col>
            <Col xs={24} sm={12} md={8} xl={4}>
              <Card><Statistic title="Windows" value={windowsCount} /></Card>
            </Col>
            <Col xs={24} sm={12} md={8} xl={4}>
              <Card><Statistic title="МФУ" value={mfuCount} /></Card>
            </Col>
            <Col xs={24} sm={12} md={8} xl={4}>
              <Card><Statistic title="Proxmox" value={proxmoxCount} /></Card>
            </Col>
            <Col xs={24} sm={12} md={8} xl={4}>
              <Card>
                <Statistic
                  title="Конфликты конфигов"
                  value={driftDevices.length}
                  valueStyle={{ color: driftDevices.length ? '#faad14' : undefined }}
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} md={8} xl={4}>
              <Card>
                <Statistic
                  title="Ошибки (7 дней)"
                  value={eventsAvailable ? errorCount7d : '—'}
                  valueStyle={{ color: errorCount7d ? '#f5222d' : undefined }}
                />
              </Card>
            </Col>
          </Row>

          <Row gutter={[16, 16]}>
            <Col xs={24} lg={12}>
              <Card title="🔄 Распределение по типам устройств">
                <div style={{ height: 300 }}>
                  <Pie data={pieData} options={{ responsive: true, maintainAspectRatio: false }} />
                </div>
              </Card>
            </Col>

            <Col xs={24} lg={12}>
              <Card title="📈 Динамика изменений и ошибок (7 дней)">
                {!eventsAvailable ? (
                  <Empty description="Журнал событий недоступен для вашей роли" />
                ) : (
                  <div style={{ height: 300 }}>
                    <Line data={lineData} options={{ responsive: true, maintainAspectRatio: false }} />
                  </div>
                )}
              </Card>
            </Col>
          </Row>

          <Card
            title="⚠️ Конфликты конфигураций (устройство ↔ база)"
            style={{ marginTop: 16 }}
            extra={
              driftDevices.length > 0
                ? <Tag color="warning">{driftDevices.length} конфликтов</Tag>
                : <Tag color="success">Конфликтов нет</Tag>
            }
          >
            {driftDevices.length === 0 ? (
              <Empty description="Расхождений конфигурации не обнаружено" />
            ) : (
              <Table
                rowKey="id"
                columns={driftColumns}
                dataSource={driftDevices}
                pagination={{ pageSize: 5 }}
                size="middle"
              />
            )}
          </Card>

          <Card
            title="❌ Последние ошибки"
            style={{ marginTop: 16 }}
            extra={
              eventsAvailable && errorEvents.length > 0
                ? <Tag color="error">{errorEvents.length} событий</Tag>
                : eventsAvailable
                  ? <Tag color="success">Всё в порядке</Tag>
                  : null
            }
          >
            {!eventsAvailable ? (
              <Alert
                type="info"
                showIcon
                message="Журнал событий недоступен для вашей роли"
                description="Просмотр ошибок доступен ролям ADMIN и AUDITOR."
              />
            ) : errorEvents.length === 0 ? (
              <Empty description="Нет записей об ошибках" />
            ) : (
              <Table
                rowKey="id"
                columns={errorColumns}
                dataSource={errorEvents}
                pagination={{ pageSize: 8 }}
                size="middle"
              />
            )}
          </Card>
        </>
      )}

      <ResolveDriftModal
        open={!!driftDevice}
        device={driftDevice ? {
          id: driftDevice.id!,
          hostname: driftDevice.hostname,
          operatingSystem: driftDevice.operatingSystem,
          typeCode: driftDevice.typeCode,
        } : null}
        onClose={() => setDriftDevice(null)}
        onResolved={() => { setDriftDevice(null); loadData(); }}
      />
    </div>
  );
}
