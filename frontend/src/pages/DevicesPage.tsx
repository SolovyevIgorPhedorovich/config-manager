import React, { useEffect, useState } from 'react';
import { Card, Table, Space, Button, Tag, Empty, message } from 'antd';
import { devicesApi } from '../api/devicesApi';
import type { Device } from '../types';
import SSHClient from '../components/SSHClient';
import { CodeOutlined, ScanOutlined, PlusCircleOutlined } from "@ant-design/icons"
import { ScanDeviceModal } from '../components/ScanDeviceModal';
import { AddDeviceModal } from '../components/AddDeviceModal';
import { ScanOption } from "../types"
import { ScanProgress } from '../components/ScanProgress';

const deviceTypeInfo: Record<string, { name: string; color: string }> = {
  windows: { name: 'ПК', color: '#1890ff' },
  mfu: { name: 'МФУ', color: '#52c41a' },
  cisco: { name: 'Cisco', color: '#fa8b0f' },
  vm: { name: 'VM', color: '#f53f3f' },
};

export default function DevicesPage({ type }: { type?: string }) {
  const [devices, setDevices] = useState<Device[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedDevice, setSelectedDevice] = useState<Device | null>(null); 
  const [showSSH, setShowSSH] = useState(false);
  const [showScan, setShowScan] = useState(false);
  const [showAddDevice, setShowAddDevice] = useState(false);
  const [isScanning, setIsScanning] = useState(false);
  const [scanProgress, setScanProgress] = useState(0);

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
    {
    title: 'Действия',
    key: 'actions',
    render: (_: any, record: Device) => (
      <Space size="small">
        {/* Кнопка SSH */}
        <Button
          icon={<CodeOutlined />}
          onClick={() => {setSelectedDevice(record); setShowSSH(true)}}
          type="default"
        >
          SSH
        </Button>
      </Space>
    ),
  },
  ];

  const handleAddDevice = async (device: Partial<Device>) => {
    try {
      const res = await devicesApi.add(device as Device);
      message.success('Устройство успешно добавлено');
      setDevices(prev => [...prev, res.data]);
    } catch (err) {
      console.error(err);
      message.error('Не удалось добавить устройство');
    }
  };

  const handleScan = async (options: any) => {
  setShowScan(false);
  setLoading(true);

  try {
    setShowScan(false);
    setIsScanning(true);
    setScanProgress(0);
    const { ipaddr, mask, port, community, snmpv } = options;

    const urlStart = new URL('/api/devices/scan', window.location.origin);
    urlStart.searchParams.append('ipaddr', ipaddr);
    urlStart.searchParams.append('mask', String(mask));
    urlStart.searchParams.append('port', String(port));
    urlStart.searchParams.append('community', community);
    urlStart.searchParams.append('snmpv', snmpv);

    const startRes = await fetch(urlStart.toString(), { method: 'GET' });
    if (!startRes.ok) throw new Error(`Ошибка запуска: ${await startRes.text()}`);
    
    const startData = await startRes.json();
    if (startData.error) throw new Error(startData.error);

    const taskId = startData.taskId;
    message.success('Сканирование запущено!');

    let statusData: any = { status: 'running' };
    let progress = 0;
    while (statusData.status === 'running') {
      await new Promise(r => setTimeout(r, 1000));

      const urlStatus = new URL('/api/devices/scan/status', window.location.origin);
      urlStatus.searchParams.append('taskId', taskId);

      const statusRes = await fetch(urlStatus.toString());
      if (!statusRes.ok) throw new Error(`Ошибка статуса: ${await statusRes.text()}`);
      
      statusData = await statusRes.json();
      progress += 10;
      setScanProgress(Math.min(progress, 90)); 
    }

    if (statusData.status === 'completed') {
      const newDevices: Device[] = statusData.devices || [];
      setDevices(prev => {
        const existingIps = new Set(prev.map(d => d.ip));
        return [
          ...prev,
          ...newDevices.filter(d => !existingIps.has(d.ip))
        ];
      });
      message.success(`Найдено и добавлено ${statusData.count} новых устройств`);
    }

  } catch (err: any) {
    console.error(err);
    message.error(`Ошибка сканирования: ${err.message || 'Неизвестная ошибка'}`);
  } finally {
    setLoading(false);
  }
};


  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <h2>
          Устройства:
          {type ? ` ${deviceTypeInfo[type]?.name}` : ' Всего'} 
          <span style={{ marginLeft: 16, fontSize: '0.9em', color: '#666' }}>
            ({devices.length} шт.)
          </span>
        </h2>

        <Space>
          <Button
            icon={<ScanOutlined />}
            type="primary"
            onClick={() => setShowScan(true)}
          >
            Сканировать сеть
          </Button>

          <Button
            icon={<PlusCircleOutlined />}
            type="default"
            onClick={() => setShowAddDevice(true)}
          >
            Добавить устройство
          </Button>
        </Space>
      </div>

      {
        isScanning && <ScanProgress progress={scanProgress} />
      }

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

      {/* Модалки */}
      <SSHClient
        open={showSSH}
        onClose={() => setShowSSH(false)}
        device={{
          hostname: selectedDevice?.hostname || '',
          ip: selectedDevice?.ip || '',
        }}
      />
      <ScanDeviceModal
        open={showScan}
        onCancel={() => setShowScan(false)}
        onScan={handleScan}
      />
      <AddDeviceModal
        open={showAddDevice}
        onCancel={() => setShowAddDevice(false)}
        onAdd={handleAddDevice}
      />
    </>
  );
}
