import React, { useEffect, useMemo, useState } from 'react';
import { Card, Table, Space, Button, Tag, Empty, message, Tabs, Typography, Select, DatePicker, Input, AutoComplete } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { devicesApi } from '../api/devicesApi';
import type { Device } from '../types';
import SSHClient from '../components/SSHClient';
import { CodeOutlined, ScanOutlined, PlusCircleOutlined, DeleteOutlined, ReloadOutlined, DiffOutlined, FileSearchOutlined, FileDoneOutlined, DownloadOutlined } from '@ant-design/icons';
import { ScanDeviceModal } from '../components/ScanDeviceModal';
import { AddDeviceModal } from '../components/AddDeviceModal';
import { ScanProgress } from '../components/ScanProgress';

const { Text, Paragraph } = Typography;

const deviceTypeInfo: Record<string, { name: string; color: string }> = {
  windows: { name: 'ПК', color: '#1890ff' },
  mfu: { name: 'МФУ', color: '#52c41a' },
  cisco: { name: 'Сетевое', color: '#fa8b0f' },
  vm: { name: 'ПК', color: '#f53f3f' },
};

type DeviceRuntimeStatus = 'online' | 'offline' | 'error';

const commandSuggestions = ['show running-config', 'show version', 'ipconfig /all', 'hostname', 'reload'];

export default function DevicesPage({ type }: { type?: string }) {
  const [devices, setDevices] = useState<Device[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedDevice, setSelectedDevice] = useState<Device | null>(null);
  const [showSSH, setShowSSH] = useState(false);
  const [showScan, setShowScan] = useState(false);
  const [showAddDevice, setShowAddDevice] = useState(false);
  const [isScanning, setIsScanning] = useState(false);
  const [scanProgress, setScanProgress] = useState(0);
  const [terminalCommand, setTerminalCommand] = useState('');

  useEffect(() => {
    loadDevices();
  }, [type]);

  const runtimeStatus = (device: Device): DeviceRuntimeStatus => {
    if (device.isActive) return 'online';
    return device.hostname.toLowerCase().includes('err') ? 'error' : 'offline';
  };

  const statusColor = (status: DeviceRuntimeStatus) => (status === 'online' ? 'success' : status === 'offline' ? 'default' : 'error');
  const statusText = (status: DeviceRuntimeStatus) => (status === 'online' ? 'Онлайн' : status === 'offline' ? 'Оффлайн' : 'Ошибка');

  const loadDevices = async () => {
    try {
      const res = await devicesApi.getAll();
      if (type && type in deviceTypeInfo) {
        const typeId = Object.keys(deviceTypeInfo).indexOf(type);
        setDevices(res.data.filter((d) => d.typeCode === typeId));
      } else {
        setDevices(res.data);
      }
    } catch (err) {
      message.error('Ошибка загрузки устройств');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = (id?: number) => {
    if (!id) return;
    setDevices((prev) => prev.filter((d) => d.id !== id));
    message.success('Устройство удалено');
  };

  const deviceColumns: ColumnsType<Device> = [
    { title: 'IP-адрес', key: 'ip', render: (_, record) => record.ips?.[0] || '—' },
    { title: 'Имя устройства', dataIndex: 'hostname', key: 'hostname' },
    {
      title: 'Тип',
      key: 'type',
      render: (_, record) => {
        const typeName = Object.keys(deviceTypeInfo)[record.typeCode] || 'unknown';
        return <Tag color={deviceTypeInfo[typeName]?.color || '#999'}>{deviceTypeInfo[typeName]?.name || 'Неизвестно'}</Tag>;
      },
    },
    {
      title: 'Статус',
      key: 'status',
      render: (_, record) => {
        const status = runtimeStatus(record);
        return <Tag color={statusColor(status)}>{statusText(status)}</Tag>;
      },
    },
    {
      title: 'Последняя синхронизация',
      key: 'sync',
      render: (_, record) => (record.createdAt ? new Date(record.createdAt).toLocaleString('ru-RU') : '—'),
    },
    {
      title: 'Действия',
      key: 'actions',
      render: (_, record) => (
        <Space size="small" wrap>
          <Button icon={<CodeOutlined />} onClick={() => { setSelectedDevice(record); setShowSSH(true); }}>Конфигурация</Button>
          <Button icon={<ScanOutlined />} onClick={() => message.info(`Инвентаризация запущена для ${record.hostname}`)}>Инвентаризация</Button>
          <Button danger icon={<DeleteOutlined />} onClick={() => handleDelete(record.id)}>Удалить</Button>
        </Space>
      ),
    },
  ];

  const diffRows = useMemo(() => ([
    { key: 1, line: '+ interface Gi0/1\n+ description Uplink to Core', type: 'add' },
    { key: 2, line: '- shutdown', type: 'remove' },
  ]), []);

  const auditRows = devices.slice(0, 8).map((d, idx) => ({
    key: d.id || idx,
    datetime: new Date(Date.now() - idx * 3600_000).toLocaleString('ru-RU'),
    user: idx % 2 === 0 ? 'admin' : 'operator',
    action: ['Создание', 'Изменение', 'Откат', 'Вход'][idx % 4],
    device: d.hostname,
    result: idx % 3 === 0 ? 'Ошибка' : 'Успех',
    deviceType: Object.keys(deviceTypeInfo)[d.typeCode] || 'windows',
  }));

  return (
    <>
      <Tabs
        defaultActiveKey="devices"
        items={[
          {
            key: 'devices',
            label: 'Список устройств',
            children: (
              <>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                  <h2>Устройства: {type ? ` ${deviceTypeInfo[type]?.name}` : ' Всего'} <span style={{ marginLeft: 16, fontSize: '0.9em', color: '#666' }}>({devices.length} шт.)</span></h2>
                  <Space>
                    <Button icon={<ScanOutlined />} type="primary" onClick={() => setShowScan(true)}>Сканировать сеть</Button>
                    <Button icon={<PlusCircleOutlined />} onClick={() => setShowAddDevice(true)}>Добавить устройство</Button>
                  </Space>
                </div>
                {isScanning && <ScanProgress progress={scanProgress} />}
                {loading ? <div style={{ textAlign: 'center', padding: 40 }}>Загрузка...</div> : devices.length === 0 ? <Empty description="Нет устройств" /> : (
                  <Card><Table rowKey="id" columns={deviceColumns} dataSource={devices} scroll={{ x: 1100 }} /></Card>
                )}
              </>
            ),
          },
          {
            key: 'config',
            label: 'Просмотр и сравнение конфигурации',
            children: (
              <Card title="Работа с конфигурациями устройства">
                <Tabs
                  items={[
                    { key: 'current', label: 'Текущая', children: <pre style={{ background: '#111', color: '#7CFC00', padding: 12 }}>{`hostname ${selectedDevice?.hostname || 'Device01'}\ninterface Gi0/1\n ip address 10.0.0.1 255.255.255.0\n no shutdown`}</pre> },
                    { key: 'history', label: 'История', children: <Paragraph>Версии конфигурации за последние 30 дней.</Paragraph> },
                    { key: 'compare', label: 'Сравнение', children: <div>{diffRows.map((r) => <pre key={r.key} style={{ background: r.type === 'add' ? '#f6ffed' : '#fff1f0', color: r.type === 'add' ? '#237804' : '#a8071a', padding: 8 }}>{r.line}</pre>)}</div> },
                    { key: 'templates', label: 'Шаблоны', children: <Paragraph>Шаблоны baseline для типов устройств.</Paragraph> },
                  ]}
                />
                <Space>
                  <Button icon={<ReloadOutlined />}>Откатить</Button>
                  <Button type="primary" icon={<FileDoneOutlined />}>Применить шаблон</Button>
                  <Button icon={<DownloadOutlined />}>Выгрузить</Button>
                </Space>
              </Card>
            ),
          },
          {
            key: 'terminal',
            label: 'Встроенный терминал',
            children: (
              <Card title="Административный терминал" extra={<Tag color={selectedDevice ? 'green' : 'orange'}>{selectedDevice ? 'Подключено' : 'Нет подключения'}</Tag>}>
                <Space direction="vertical" style={{ width: '100%' }}>
                  <AutoComplete style={{ width: '100%' }} options={commandSuggestions.map((value) => ({ value }))} value={terminalCommand} onChange={setTerminalCommand} placeholder="Введите команду" />
                  <pre style={{ background: '#101820', color: '#e6f7ff', minHeight: 180, padding: 12 }}>$ {terminalCommand || 'show running-config'}\nВывод команды...</pre>
                  <Space>
                    <Button type="primary" icon={<CodeOutlined />} onClick={() => setShowSSH(true)}>Открыть xterm.js</Button>
                    <Button icon={<FileSearchOutlined />}>Захватить конфигурацию</Button>
                  </Space>
                  <Text type="secondary">Сессии терминала изолированы. Логи сессий сохраняются в журнале аудита.</Text>
                </Space>
              </Card>
            ),
          },
          {
            key: 'audit',
            label: 'Журнал аудита',
            children: (
              <Card title="Отчет по действиям пользователей" extra={<Space><Button>CSV</Button><Button>PDF</Button></Space>}>
                <Space wrap style={{ marginBottom: 12 }}>
                  <DatePicker.RangePicker />
                  <Select placeholder="Пользователь" style={{ width: 180 }} options={[{ value: 'admin' }, { value: 'operator' }]} />
                  <Select placeholder="Тип устройства" style={{ width: 180 }} options={Object.entries(deviceTypeInfo).map(([k, v]) => ({ value: k, label: v.name }))} />
                </Space>
                <Table columns={[{ title: 'Дата/время', dataIndex: 'datetime' }, { title: 'Пользователь', dataIndex: 'user' }, { title: 'Действие', dataIndex: 'action' }, { title: 'Устройство', dataIndex: 'device' }, { title: 'Результат', dataIndex: 'result', render: (v: string) => <Tag color={v === 'Успех' ? 'success' : 'error'}>{v}</Tag> }]} dataSource={auditRows} />
              </Card>
            ),
          },
        ]}
      />

      <SSHClient open={showSSH} onClose={() => setShowSSH(false)} device={selectedDevice ? { hostname: selectedDevice.hostname, ip: selectedDevice.ips?.[0] || '' } : null} />
      <ScanDeviceModal open={showScan} onCancel={() => setShowScan(false)} onScan={async () => { setShowScan(false); setIsScanning(true); setScanProgress(75); await loadDevices(); setIsScanning(false); message.success('Сканирование завершено'); }} />
      <AddDeviceModal open={showAddDevice} onCancel={() => setShowAddDevice(false)} onAdd={async (device: Partial<Device>) => { const res = await devicesApi.add(device as Device); setDevices((prev) => [...prev, res.data]); setShowAddDevice(false); }} />
    </>
  );
}


