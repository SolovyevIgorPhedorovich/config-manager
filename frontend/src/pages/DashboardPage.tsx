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

const deviceTypeInfo: Record<string, { name: string; color: string }> = {
  windows: { name: 'ПК', color: '#1890ff' },
  linux: { name: 'МФУ', color: '#52c41a' },
  cisco_switch: { name: 'Cisco', color: '#fa8b0f' },
  proxmox: { name: 'VM', color: '#f53f3f' },
};

const dashboardDemoDevices: Device[] = Array.from({ length: 28 }, (_, index) => {
  const typeCode = (index % 4) as Device['typeCode'];
  const typeNames = ['ПК', 'МФУ', 'CISCO', 'VM'];

  return {
    id: 20_000 + index,
    hostname: `${typeNames[typeCode].toLowerCase()}-${String(index + 1).padStart(2, '0')}`,
    ips: [`172.16.${Math.floor(index / 128)}.${index + 10}`],
    typeCode,
    type: typeNames[typeCode],
    groupName: index % 2 === 0 ? 'ЦОД' : 'Офис',
    osVersion: typeCode === 2 ? 'IOS XE 17.9' : typeCode === 3 ? 'Ubuntu 22.04' : 'Windows 11',
    isActive: index % 6 !== 0,
    createdAt: new Date(Date.now() - index * 3_600_000).toISOString(),
  };
});

const dashboardDemoAuditLogs: AuditLog[] = Array.from({ length: 14 }, (_, index) => ({
  id: 30_000 + index,
  userId: index % 2 === 0 ? 'admin' : 'operator',
  actionType: ['DEPLOY_CONFIG', 'ROLLBACK', 'LOGIN', 'SCAN'][index % 4],
  targetDeviceId: dashboardDemoDevices[index % dashboardDemoDevices.length].id,
  status: index % 5 === 0 ? 'FAILED' : index % 3 === 0 ? 'RUNNING' : 'SUCCESS',
  createdAt: new Date(Date.now() - index * 3_600_000).toISOString(),
}));

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

  const chartData = useMemo(() => Object.entries(deviceTypeInfo).map(([typeKey, info]) => ({
    typeKey,
    name: info.name,
    count: devices.filter((device) => Object.keys(deviceTypeInfo)[device.typeCode!] === typeKey).length,
    color: info.color,
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
  const windowsCount = chartData.find(d => d.typeKey === 'windows')?.count || 0;
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
          <Row gutter={16} style={{ marginBottom: 24 }}>
            <Col xs={24} sm={12} md={6}>
              <Card>
                <Statistic
                  title="Всего устройств"
                  value={totalDevices}
                  suffix={<Tag color="#1890ff">всё</Tag>}
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Card>
                <Statistic
                  title="Windows ПК"
                  value={windowsCount}
                  suffix={<Tag color="#1890ff">ОС</Tag>}
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Card>
                <Statistic
                  title="Сетевое оборудование"
                  value={
                    chartData.find(d => d.typeKey === 'cisco_switch')?.count || 0
                  }
                  suffix={<Tag color="#fa8b0f">Сеть</Tag>}
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Card>
                <Statistic
                  title="Ошибок за сутки"
                  value={errorCount}
                  suffix={<Tag color="warning">⚠️</Tag>}
                  valueStyle={{ color: errorCount > 0 ? '#ff4d4f' : '#52c41a' }}
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
