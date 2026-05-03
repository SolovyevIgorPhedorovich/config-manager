import React, { useEffect, useState } from 'react';
import { Card, Table, Tag, Empty, message } from 'antd';
import { devicesApi } from '../api/devicesApi';
import type { Device } from '../types';

const deviceTypeInfo: Record<string, { name: string; color: string }> = {
  windows: { name: 'ПК', color: '#1890ff' },
  mfu: { name: 'МФУ', color: '#52c41a' },
  cisco: { name: 'Cisco', color: '#fa8b0f' },
  vm: { name: 'VM', color: '#f53f3f' },
};

export default function DevicesPage({ type }: { type?: string }) {
  const [devices, setDevices] = useState<Device[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadDevices();
  }, [type]);

  const loadDevices = async () => {
    try {
      const res = await devicesApi.getAll();
      
      // Фильтрация по типу
      if (type && type in deviceTypeInfo) {
        const typeId = Object.keys(deviceTypeInfo).indexOf(type);
        setDevices(res.data.filter(d => d.typeCode === typeId));
      } else {
        setDevices(res.data);
      }
    } catch (err) {
      message.error('Ошибка загрузки устройств');
    } finally {
      setLoading(false);
    }
  };

  const columns = [
    { title: 'Hostname', dataIndex: 'hostname', key: 'hostname' },
    { title: 'IP-адрес', dataIndex: 'ip', key: 'ip' },
    {
      title: 'Тип',
      render: (_: any, record: Device) => {
        const typeName = Object.keys(deviceTypeInfo)[record.typeCode!] || 'unknown';
        return (
          <Tag color={deviceTypeInfo[typeName]?.color || '#999'}>
            {deviceTypeInfo[typeName]?.name || 'Неизвестно'}
          </Tag>
        );
      },
    },
  ];

  return (
    <>
      <h2>
        Устройства: {type ? deviceTypeInfo[type]?.name : 'Все типы'}
        <span style={{ marginLeft: 16, fontSize: '0.9em', color: '#666' }}>
          ({devices.length} шт.)
        </span>
      </h2>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 40 }}>Загрузка...</div>
      ) : devices.length === 0 ? (
        <Empty description="Нет устройств для отображения" />
      ) : (
        <Card>
          <Table
            rowKey="id"
            columns={columns}
            dataSource={devices}
            pagination={{ pageSize: 10 }}
          />
        </Card>
      )}
    </>
  );
}
