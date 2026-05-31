import React, { useEffect, useMemo, useState } from 'react';
import {
  Card, Table, Tag, Typography, Row, Col, Statistic,
  Empty
} from 'antd';
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
      const devicesRes = await devicesApi.getAll();
      setDevices(devicesRes.data);

      const logsRes = await auditApi.getLogs({ size: 10 });
      setAuditLogs(logsRes.data);
    } catch (err) {
      setDevices([]);
      setAuditLogs([]);
      console.error('Ошибка загрузки данных:', err);
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

  const totalDevices = devices.length;
  const pcCount = devices.filter((device) => device.typeCode === 0 || device.type === 'ПК').length;
  const linuxCount = chartData.find(d => d.typeKey === 'linux')?.count || 0;
  const windowsCount = chartData.find(d => d.typeKey === 'windows')?.count || 0;
  const mfuCount = chartData.find(d => d.typeKey === 'mfu')?.count || 0;
  const proxmoxCount = chartData.find(d => d.typeKey === 'proxmox')?.count || 0;
  const errorCount = auditLogs.filter(l => l.status === 'FAILED').length;

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
          <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
            <Col xs={24} sm={12} md={8} xl={4}>
              <Card>
                <Statistic title="Всего" value={totalDevices} />
              </Card>
            </Col>

            <Col xs={24} sm={12} md={8} xl={4}>
              <Card>
                <Statistic title="ПК" value={pcCount} />
              </Card>
            </Col>

            <Col xs={24} sm={12} md={8} xl={4}>
              <Card>
                <Statistic title="Linux" value={linuxCount} />
              </Card>
            </Col>

            <Col xs={24} sm={12} md={8} xl={4}>
              <Card>
                <Statistic title="Windows" value={windowsCount} />
              </Card>
            </Col>

            <Col xs={24} sm={12} md={8} xl={4}>
              <Card>
                <Statistic title="МФУ" value={mfuCount} />
              </Card>
            </Col>

            <Col xs={24} sm={12} md={8} xl={4}>
              <Card>
                <Statistic title="Proxmox" value={proxmoxCount} />
              </Card>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col xs={24} lg={12}>
              <Card title="🔄 Распределение по типам устройств">
                <Pie data={pieData} options={{ responsive: true, maintainAspectRatio: false }} height={300} />
              </Card>
            </Col>

            <Col xs={24} lg={12}>
              <Card title="📈 Динамика изменений (последние 7 дней)">
                <Line data={lineData} options={{ responsive: true, maintainAspectRatio: false }} height={300} />
              </Card>
            </Col>
          </Row>

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
