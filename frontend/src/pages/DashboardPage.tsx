import React, { useEffect, useMemo, useState } from 'react';
import {
  Card, Table, Tag, Typography, Row, Col, Statistic,
  Empty
} from 'antd';
import {
  Pie, Line
} from 'react-chartjs-2';
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
import { auditApi } from '../api/auditApi';
import type { Device, AuditLog } from '../types';

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

const dashboardDemoDevices: Device[] = [
  ...Array.from({ length: 11 }, (_, index) => ({
    id: 20_000 + index,
    hostname: `пк-${String(index + 1).padStart(2, '0')}`,
    ips: [`172.16.0.${index + 10}`],
    typeCode: 0 as const,
    type: 'ПК',
    groupName: index % 2 === 0 ? 'ЦОД' : 'Офис',
    osVersion: 'Windows 11',
    isActive: index % 5 !== 0,
    createdAt: new Date(Date.now() - index * 3_600_000).toISOString(),
  })),
  ...Array.from({ length: 6 }, (_, index) => ({
    id: 20_100 + index,
    hostname: `linux-${String(index + 1).padStart(2, '0')}`,
    ips: [`172.16.1.${index + 10}`],
    typeCode: 0 as const,
    type: 'ПК',
    groupName: 'Linux-серверы',
    osVersion: index % 2 === 0 ? 'Ubuntu 22.04' : 'Debian 12',
    isActive: index % 4 !== 0,
    createdAt: new Date(Date.now() - (index + 11) * 3_600_000).toISOString(),
  })),
  ...Array.from({ length: 4 }, (_, index) => ({
    id: 20_200 + index,
    hostname: `мфу-${String(index + 1).padStart(2, '0')}`,
    ips: [`172.16.2.${index + 10}`],
    typeCode: 1 as const,
    type: 'МФУ',
    groupName: 'Офис',
    osVersion: 'Firmware 4.2',
    isActive: true,
    createdAt: new Date(Date.now() - (index + 17) * 3_600_000).toISOString(),
  })),
  ...Array.from({ length: 5 }, (_, index) => ({
    id: 20_300 + index,
    hostname: `cisco-${String(index + 1).padStart(2, '0')}`,
    ips: [`172.16.3.${index + 10}`],
    typeCode: 2 as const,
    type: 'CISCO',
    groupName: 'Сеть',
    osVersion: 'IOS XE 17.9',
    isActive: index !== 3,
    createdAt: new Date(Date.now() - (index + 21) * 3_600_000).toISOString(),
  })),
  ...Array.from({ length: 3 }, (_, index) => ({
    id: 20_400 + index,
    hostname: `proxmox-${String(index + 1).padStart(2, '0')}`,
    ips: [`172.16.4.${index + 10}`],
    typeCode: 3 as const,
    type: 'VM',
    groupName: 'Proxmox',
    osVersion: 'Proxmox VE 8',
    isActive: true,
    createdAt: new Date(Date.now() - (index + 26) * 3_600_000).toISOString(),
  })),
];

const dashboardDemoAuditLogs: AuditLog[] = Array.from({ length: 14 }, (_, index) => ({
  id: 30_000 + index,
  userId: index % 2 === 0 ? 'admin' : 'operator',
  actionType: ['DEPLOY_CONFIG', 'ROLLBACK', 'LOGIN', 'SCAN'][index % 4],
  targetDeviceId: dashboardDemoDevices[index % dashboardDemoDevices.length].id,
  status: index % 5 === 0 ? 'FAILED' : index % 3 === 0 ? 'RUNNING' : 'SUCCESS',
  createdAt: new Date(Date.now() - index * 3_600_000).toISOString(),
}));

const getDeviceCategory = (device: Device) => {
  const type = (device.type || '').toLowerCase();
  const osVersion = (device.osVersion || '').toLowerCase();

  if (device.typeCode === 1 || type.includes('мфу')) return 'mfu';
  if (device.typeCode === 2 || type.includes('cisco')) return 'cisco';
  if (device.typeCode === 3 || type.includes('vm') || osVersion.includes('proxmox')) return 'proxmox';
  if (osVersion.includes('linux') || osVersion.includes('ubuntu') || osVersion.includes('debian')) return 'linux';
  return 'windows';
};

export default function DashboardPage() {
  const [devices, setDevices] = useState<Device[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      // Загрузка устройств
      const devicesRes = await devicesApi.getAll();
      const mergedDevices = [
        ...devicesRes.data,
        ...dashboardDemoDevices.filter((demoDevice) => !devicesRes.data.some((device) => device.id === demoDevice.id)),
      ];
      setDevices(mergedDevices);

      // Загрузка последних 10 логов аудита
      const logsRes = await auditApi.getLogs({ size: 10 });
      const mergedLogs = [
        ...logsRes.data,
        ...dashboardDemoAuditLogs.filter((demoLog) => !logsRes.data.some((log) => log.id === demoLog.id)),
      ];
      setAuditLogs(mergedLogs);

    } catch (err) {
      setDevices(dashboardDemoDevices);
      setAuditLogs(dashboardDemoAuditLogs);
      console.error('Ошибка загрузки данных:', err);
    } finally {
      setLoading(false);
    }
  };

  const chartData = useMemo(() => dashboardCategories.map((category) => ({
    typeKey: category.key,
    name: category.name,
    count: devices.filter((device) => getDeviceCategory(device) === category.key).length,
    color: category.color,
  })), [devices]);

  // Данные для круговой диаграммы
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

  // Данные для линейного графика (последние изменения)
  const lineData = {
    labels: auditLogs.slice(0, 7).map(log =>
        new Date(log.createdAt!).toLocaleDateString('ru-RU')
    ),
    datasets: [
        {
            label: 'Количество изменений',
            data: Array(auditLogs.slice(0, 7).length).fill(1),
            borderColor: '#1890ff',
            tension: 0.3,
        },
    ],
  };

  const errorColumns = [
    {
      title: 'Устройство',
      key: 'hostname',
      render: (_: any, log: AuditLog) => (
        <Text>{log.targetDeviceId ? `ID ${log.targetDeviceId}` : '-'}</Text>
      ),
    },
    { title: 'Действие', dataIndex: 'actionType', key: 'actionType' },
    {
      title: 'Статус',
      dataIndex: 'status',
      render: (status: string) => (
        <Tag color={status === 'FAILED' ? 'error' : status === 'SUCCESS' ? 'success' : 'processing'}>
          {status}
        </Tag>
      ),
    },
    { title: 'Время', dataIndex: 'createdAt', key: 'createdAt' },
  ];

   // Статистика
  const totalDevices = devices.length;
  const pcCount = devices.filter((device) => device.typeCode === 0 || device.type === 'ПК').length;
  const linuxCount = chartData.find(d => d.typeKey === 'linux')?.count || 0;
  const windowsCount = chartData.find(d => d.typeKey === 'windows')?.count || 0;
  const mfuCount = chartData.find(d => d.typeKey === 'mfu')?.count || 0;
  const proxmoxCount = chartData.find(d => d.typeKey === 'proxmox')?.count || 0;
  const errorCount = auditLogs.filter(l => l.status === 'FAILED').length;

  return (
    <div style={{ padding: '24px' }}>
      {/* Заголовок */}
      <Title level={2}>📊 Панель управления</Title>
      <Text type="secondary">Общая информация о состоянии ИТ-инфраструктуры</Text>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 40 }}>
          Загрузка данных...
        </div>
      ) : (
        <>
          {/* Статистические карточки */}
          <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
            <Col xs={24} sm={12} md={8} xl={4}>
              <Card>
                <Statistic
                  title="Всего"
                  value={totalDevices}
                  suffix={<Tag color="#1890ff">всё</Tag>}
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} md={8} xl={4}>
              <Card>
                <Statistic
                  title="ПК"
                  value={pcCount}
                  suffix={<Tag color="#1890ff">АРМ</Tag>}
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} md={8} xl={4}>
              <Card>
                <Statistic
                  title="Linux"
                  value={linuxCount}
                  suffix={<Tag color="#722ed1">OS</Tag>}
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} md={8} xl={4}>
              <Card>
                <Statistic
                  title="Windows"
                  value={windowsCount}
                  suffix={<Tag color="#1890ff">OS</Tag>}
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} md={8} xl={4}>
              <Card>
                <Statistic
                  title="МФУ"
                  value={mfuCount}
                  suffix={<Tag color="#52c41a">печать</Tag>}
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} md={8} xl={4}>
              <Card>
                <Statistic
                  title="Proxmox"
                  value={proxmoxCount}
                  suffix={<Tag color="#f53f3f">серверы</Tag>}
                />
              </Card>
            </Col>
          </Row>

          {/* Диаграммы и таблицы */}
          <Row gutter={16}>
            <Col xs={24} lg={12}>
              <Card title="🔄 Распределение по типам устройств">
                <Pie
                  data={pieData}
                  options={{
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                      legend: { position: 'right' },
                    },
                  }}
                  height={300}
                />
              </Card>
            </Col>

            <Col xs={24} lg={12}>
              <Card title="📈 Динамика изменений (последние 7 дней)">
                <Line
                  data={lineData}
                  options={{
                    responsive: true,
                    maintainAspectRatio: false,
                    scales: {
                      y: { beginAtZero: true },
                    },
                  }}
                  height={300}
                />
              </Card>
            </Col>
          </Row>

          {/* Таблица ошибок */}
          <Card
            title="❌ Ошибки"
            extra={
              errorCount > 0 ? (
                <Tag color="error">{errorCount} событий</Tag>
              ) : (
                <Tag color="success">Всё в порядке</Tag>
              )
            }
          >
            {auditLogs.length === 0 ? (
              <Empty description="Нет записей об ошибках" />
            ) : (
              <Table
                rowKey="id"
                columns={errorColumns}
                dataSource={auditLogs.filter(l => l.status === 'FAILED')}
                pagination={{ pageSize: 5 }}
              />
            )}
          </Card>
        </>
      )}
    </div>
  );
}
