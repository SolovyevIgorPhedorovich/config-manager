import React, { useEffect, useState } from 'react';
import {
  Card, Table, Tag, Space, Typography, Row, Col, Statistic,
  Empty, Alert
} from 'antd';
import {
  Pie, Bar, Line
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

// Преобразуем типы в массив для диаграммы
const chartData = Object.entries(deviceTypeInfo).map(([typeKey, info]) => ({
  typeKey,
  name: info.name,
  count: 0,
  color: info.color,
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
      setDevices(devicesRes.data);

      // Обновление количества по типам
      chartData.forEach(item => item.count = 0);
      devicesRes.data.forEach(device => {
        const typeKey = Object.keys(deviceTypeInfo)[device.typeCode!] || 'unknown';
        const existingItem = chartData.find(i => i.typeKey === typeKey);
        if (existingItem) existingItem.count++;
      });

      // Загрузка последних 10 логов аудита
      const logsRes = await auditApi.getLogs({ size: 10 });
      setAuditLogs(logsRes.data);

    } catch (err) {
      console.error('Ошибка загрузки данных:', err);
    } finally {
      setLoading(false);
    }
  };

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
                    (chartData.find(d => d.typeKey === 'cisco_switch')?.count || 0) + 
                    (chartData.find(d => d.typeKey === 'cisco_router')?.count || 0)
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
