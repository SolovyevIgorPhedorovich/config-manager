import React, { useEffect, useMemo, useState } from 'react';
import { Card, Table, Space, Button, Tag, Empty, message, Tabs, AutoComplete, DatePicker, Select, Typography, Input, Row, Col, Progress, Tooltip } from 'antd';
import dayjs from 'dayjs';
import { devicesApi } from '../api/devicesApi';
import type { ConfigVersion, Device } from '../types';
import SSHClient from '../components/SSHClient';
import { ArrowRightOutlined, CodeOutlined, ScanOutlined, PlusCircleOutlined, DeleteOutlined, ReloadOutlined, DownloadOutlined, FileDoneOutlined, FileSearchOutlined, EditOutlined } from "@ant-design/icons"
import { ScanDeviceModal } from '../components/ScanDeviceModal';
import { AddDeviceModal } from '../components/AddDeviceModal';
import { ScanProgress } from '../components/ScanProgress';
import { ColumnsType } from 'antd/es/table';
import ConfigLinuxModal from '../components/ConfigLinuxModal';
import ConfigWindowsModal from '../components/ConfigWindowsModal';
import ConfigMFUModal from '../components/ConfigMFUModal';
import ConfigCiscoModal from '../components/ConfigCiscoModal';


const { Text, Paragraph } = Typography;
const { Search } = Input;


const deviceTypeInfo: Record<string, { name: string; color: string; code: number }> = {
  ПК: { name: 'ПК', color: '#1890ff', code: 0 },
  МФУ: { name: 'МФУ', color: '#52c41a', code: 1 },
  CISCO: { name: 'Cisco', color: '#fa8b0f', code: 2 },
  VM: { name: 'VM', color: '#f53f3f', code: 3 },
};

const routeTypeMap: Record<string, keyof typeof deviceTypeInfo> = {
  windows: 'ПК',
  pc: 'ПК',
  mfu: 'МФУ',
  cisco: 'CISCO',
  vm: 'VM',
};

type DeviceRuntimeStatus = 'online' | 'offline' | 'error';
const commandSuggestions = ['show running-config', 'show version', 'ipconfig /all', 'hostname', 'reload'];

const demoDevices: Device[] = Array.from({ length: 64 }, (_, index) => {
  const typeCodes: Device['typeCode'][] = [0, 1, 2, 3];
  const typeCode = typeCodes[index % typeCodes.length];
  const typeName = Object.keys(deviceTypeInfo)[typeCode];

  return {
    id: 10_000 + index,
    hostname: `${typeName.toLowerCase()}-${String(index + 1).padStart(2, '0')}`,
    ips: [`10.20.${Math.floor(index / 254)}.${(index % 254) + 1}`],
    typeCode,
    type: typeName,
    groupName: index % 2 === 0 ? 'Главный офис' : 'Филиал',
    osVersion: typeCode === 2 ? 'IOS XE 17.9' : typeCode === 1 ? 'Firmware 4.2' : typeCode === 3 ? 'Ubuntu 22.04' : 'Windows 11',
    isActive: index % 5 !== 0,
    createdAt: new Date(Date.now() - index * 3_600_000).toISOString(),
  };
});

const previousConfig = `hostname edge-router-01
interface Gi0/0
 description WAN uplink
 ip address 10.0.0.1 255.255.255.0
 shutdown
router ospf 10
 network 10.0.0.0 0.0.0.255 area 0
logging buffered 4096`;

const currentConfig = `hostname edge-router-01
interface Gi0/0
 description WAN uplink to Core
 ip address 10.0.0.1 255.255.255.0
 no shutdown
router ospf 10
 network 10.0.0.0 0.0.0.255 area 0
service timestamps log datetime msec
logging buffered 8192`;

const demoConfigVersions: ConfigVersion[] = [
  {
    id: 501,
    deviceId: 10_000,
    configType: 2,
    versionNumber: 12,
    appliedAt: new Date().toISOString(),
    oldConfigJson: previousConfig,
    newConfig: currentConfig,
    diffHash: 'cfg-501',
    rollbackAvailable: true,
  },
  {
    id: 487,
    deviceId: 10_000,
    configType: 2,
    versionNumber: 11,
    appliedAt: new Date(Date.now() - 86_400_000).toISOString(),
    oldConfigJson: previousConfig.replace('shutdown', 'no shutdown'),
    newConfig: previousConfig,
    diffHash: 'cfg-487',
    rollbackAvailable: true,
  },
  {
    id: 455,
    deviceId: 10_001,
    configType: 0,
    versionNumber: 7,
    appliedAt: new Date(Date.now() - 2 * 86_400_000).toISOString(),
    oldConfigJson: 'hostname pc-02\nfirewall enabled\nupdates manual',
    newConfig: 'hostname pc-02\nfirewall enabled\nupdates auto',
    diffHash: 'cfg-455',
    rollbackAvailable: true,
  },
];


type DiffLine = { option: string; value: string; raw: string; status: 'added' | 'removed' | 'unchanged' };

const parseConfigLine = (line: string): Omit<DiffLine, 'status'> => {
  const trimmed = line.trim();
  const [first = '', second = '', ...rest] = trimmed.split(/\s+/);

  if (!trimmed) return { option: 'пустая строка', value: '—', raw: line };
  if (first === 'hostname') return { option: 'hostname', value: [second, ...rest].join(' '), raw: line };
  if (first === 'interface') return { option: 'interface', value: [second, ...rest].join(' '), raw: line };
  if (first === 'description') return { option: 'description', value: [second, ...rest].join(' '), raw: line };
  if (first === 'ip' && second === 'address') return { option: 'ip address', value: rest.join(' '), raw: line };
  if (first === 'no' && second === 'shutdown') return { option: 'shutdown', value: 'disabled', raw: line };
  if (first === 'shutdown') return { option: 'shutdown', value: 'enabled', raw: line };
  if (first === 'router') return { option: `router ${second}`, value: rest.join(' '), raw: line };
  if (first === 'network') return { option: 'network', value: [second, ...rest].join(' '), raw: line };
  if (first === 'service') return { option: `service ${second}`, value: rest.join(' '), raw: line };
  if (first === 'logging') return { option: `logging ${second}`, value: rest.join(' '), raw: line };

  return { option: first, value: [second, ...rest].join(' ') || 'enabled', raw: line };
};

const buildPanelDiff = (previous: string, current: string) => {
  const previousLines = previous.split('\n');
  const currentLines = current.split('\n');

  return {
    previous: previousLines.map<DiffLine>((line) => ({
      ...parseConfigLine(line),
      status: currentLines.includes(line) ? 'unchanged' : 'removed',
    })),
    current: currentLines.map<DiffLine>((line) => ({
      ...parseConfigLine(line),
      status: previousLines.includes(line) ? 'unchanged' : 'added',
    })),
  };
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
  const [terminalCommand, setTerminalCommand] = useState('');
  const [terminalConnected, setTerminalConnected] = useState(false);
  const [deviceSearch, setDeviceSearch] = useState('');
  const [selectedDeviceIds, setSelectedDeviceIds] = useState<React.Key[]>([]);
  const [auditDateRange, setAuditDateRange] = useState<[any, any] | null>(null);
  const [auditDeviceType, setAuditDeviceType] = useState<string>();
  const [auditResponsible, setAuditResponsible] = useState<string>();
  const [auditAction, setAuditAction] = useState<string>();
  const [auditDevice, setAuditDevice] = useState<string>();
  const [auditResult, setAuditResult] = useState<string>();
  const [transferredLines, setTransferredLines] = useState<Record<number, DiffLine>>({});
  const [configVersions, setConfigVersions] = useState<ConfigVersion[]>(demoConfigVersions);
  const [selectedConfigVersionId, setSelectedConfigVersionId] = useState<number>(demoConfigVersions[0].id!);
  const [linuxModalOpen, setLinuxModalOpen] = useState(false);
  const [windowsModalOpen, setWindowsModalOpen] = useState(false);
  const [mfuModalOpen, setMfuModalOpen] = useState(false);
  const [ciscoModalOpen, setCiscoModalOpen] = useState(false);

  useEffect(() => {
    loadDevices();
  }, [type]);

  useEffect(() => {
    loadConfigVersions();
  }, [selectedDevice]);

  const runtimeStatus = (device: Device): DeviceRuntimeStatus => {
    if (device.isActive) return 'online';
    return device.hostname.toLowerCase().includes('err') ? 'error' : 'offline';
  };

  const statusColor = (status: DeviceRuntimeStatus) => (status === 'online' ? 'success' : status === 'offline' ? 'default' : 'error');
  const statusText = (status: DeviceRuntimeStatus) => (status === 'online' ? 'Онлайн' : status === 'offline' ? 'Оффлайн' : 'Ошибка');

  const applyTypeFilter = (items: Device[]) => {
    const mappedType = type ? routeTypeMap[type] : undefined;

    if (!mappedType) return items;

    const expectedCode = deviceTypeInfo[mappedType].code;
    return items.filter((device) => device.type === mappedType || device.typeCode === expectedCode);
  };

  const loadDevices = async () => {
    try {
      const res = await devicesApi.getAll();
      const mergedDevices = [
        ...res.data,
        ...demoDevices.filter((demoDevice) => !res.data.some((device) => device.id === demoDevice.id)),
      ];

      setDevices(applyTypeFilter(mergedDevices));
    } catch (err) {
      setDevices(applyTypeFilter(demoDevices));
      message.error('Ошибка загрузки устройств. Показаны тестовые записи.');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = (id?: number) => {
    if (!id) return;
    setDevices((prev) => prev.filter((d) => d.id !== id));
    message.success("Устройство удалено");
  }

  const getActionProgress = (device: Device) => {
    if (runtimeStatus(device) !== 'online') return 0;

    const id = device.id || 0;
    if (id % 9 === 0) return 100;
    if (id % 5 === 0) return 72;
    if (id % 4 === 0) return 46;
    if (id % 7 === 0) return 18;
    return 0;
  };

  const getActionProgressStatus = (progress: number) => {
    if (progress >= 100) return 'success';
    if (progress > 0 && progress < 30) return 'exception';
    return 'normal';
  };

  const deviceColumns: ColumnsType<Device> = [
    { title: 'IP-адрес', key: 'ip', render: (_, record) => record.ips?.[0] || '—' },
    { title: 'Имя устройства', dataIndex: 'hostname', key: 'hostname' },
    {
      title: 'Тип',
      key: 'type',
      render: (_, record) => {
        const typeName = record.type || Object.keys(deviceTypeInfo)[record.typeCode];
        return <Tag color={deviceTypeInfo[typeName]?.color || '#999'}>{deviceTypeInfo[typeName]?.name || typeName || 'Неизвестно'}</Tag>;
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
      title: 'Выполнение',
      key: 'progress',
      render: (_, record) => {
        const progress = getActionProgress(record);
        return progress > 0 ? (
          <Progress type="circle" size={44} percent={progress} status={getActionProgressStatus(progress)} />
        ) : (
          <Text type="secondary">—</Text>
        );
      },
    },
    {
      title: 'Действия',
      key: 'actions',
      render: (_, record) => (
        <Space size="small" wrap>
          <Tooltip title="Редактировать">
            <Button aria-label="Редактировать" icon={<EditOutlined/>} onClick={() => { setSelectedDevice(record); openConfigModalByType(record)}} />
          </Tooltip>
          <Tooltip title="Конфигурация">
            <Button aria-label="Конфигурация" icon={<CodeOutlined />} onClick={() => { setSelectedDevice(record); setShowSSH(true); }} />
          </Tooltip>
          <Tooltip title="Инвентаризация">
            <Button aria-label="Инвентаризация" icon={<ScanOutlined />} onClick={() => message.info(`Инвентаризация запущена для ${record.hostname}`)} />
          </Tooltip>
          <Tooltip title="Удалить">
            <Button aria-label="Удалить" danger icon={<DeleteOutlined />} onClick={() => handleDelete(record.id)} />
          </Tooltip>
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

  const loadConfigVersions = async () => {
    if (!selectedDevice?.id) {
      setConfigVersions(demoConfigVersions);
      setSelectedConfigVersionId(demoConfigVersions[0].id!);
      setTransferredLines({});
      return;
    }

    try {
      const res = await devicesApi.getHistory(selectedDevice.id);
      const versions = res.data.length > 0 ? res.data : demoConfigVersions.filter((config) => config.deviceId === selectedDevice.id);
      const nextVersions = versions.length > 0 ? versions : demoConfigVersions;
      setConfigVersions(nextVersions);
      setSelectedConfigVersionId(nextVersions[0].id!);
      setTransferredLines({});
    } catch (err) {
      const fallbackVersions = demoConfigVersions.filter((config) => config.deviceId === selectedDevice.id);
      const nextVersions = fallbackVersions.length > 0 ? fallbackVersions : demoConfigVersions;
      setConfigVersions(nextVersions);
      setSelectedConfigVersionId(nextVersions[0].id!);
      setTransferredLines({});
    }
  };

  const selectedConfigVersion = useMemo(() => (
    configVersions.find((config) => config.id === selectedConfigVersionId) || configVersions[0]
  ), [configVersions, selectedConfigVersionId]);

  const currentConfigOptions = useMemo(() => (
    (selectedConfigVersion?.newConfig || currentConfig).split('\n').map((line) => ({ ...parseConfigLine(line), status: 'unchanged' as const }))
  ), [selectedConfigVersion]);

  const handleTransferLine = (line: DiffLine, sourceIndex: number) => {
    setTransferredLines((prev) => ({ ...prev, [sourceIndex]: { ...line, status: 'added' } }));
    message.success(`Опция перенесена в текущую конфигурацию: ${line.option}: ${line.value}`);
  };

  const diffPanels = useMemo(() => {
    const baseDiff = buildPanelDiff(
      selectedConfigVersion?.oldConfigJson || previousConfig,
      selectedConfigVersion?.newConfig || currentConfig,
    );

    let transferIndex = 0;

    return {
      previous: baseDiff.previous,
      current: baseDiff.current.map((line) => {
        if (line.status !== 'added') return line;

        const transferred = Object.values(transferredLines)[transferIndex];
        transferIndex += 1;
        return transferred || line;
      }),
    };
  }, [selectedConfigVersion, transferredLines]);

  const filteredDevices = useMemo(() => {
    const normalizedSearch = deviceSearch.trim().toLowerCase();

    if (!normalizedSearch) return devices;

    return devices.filter((device) => [
      device.hostname,
      device.groupName,
      device.osVersion,
      device.type,
      ...(device.ips || []),
    ].some((value) => value?.toLowerCase().includes(normalizedSearch)));
  }, [devices, deviceSearch]);

  const auditRows = useMemo(() => devices.slice(0, 24).map((d, idx) => {
    const timestamp = Date.now() - idx * 3600_000;

    return {
    key: d.id || idx,
    timestamp,
    datetime: new Date(timestamp).toLocaleString('ru-RU'),
    user: idx % 3 === 0 ? 'admin' : idx % 3 === 1 ? 'operator' : 'network-engineer',
    action: ['Создание', 'Изменение', 'Откат', 'Вход'][idx % 4],
    device: d.hostname,
    result: idx % 3 === 0 ? 'Ошибка' : 'Успех',
    deviceType: d.type || Object.keys(deviceTypeInfo)[d.typeCode] || 'ПК',
  };
  }), [devices]);

  const filteredAuditRows = useMemo(() => auditRows.filter((row) => {
    const rowDate = dayjs(row.timestamp);
    const matchesDate = !auditDateRange || !auditDateRange[0] || !auditDateRange[1] || (
      rowDate.isAfter(auditDateRange[0].startOf('day')) && rowDate.isBefore(auditDateRange[1].endOf('day'))
    );
    const matchesType = !auditDeviceType || row.deviceType === auditDeviceType;
    const matchesResponsible = !auditResponsible || row.user === auditResponsible;
    const matchesAction = !auditAction || row.action === auditAction;
    const matchesDevice = !auditDevice || row.device === auditDevice;
    const matchesResult = !auditResult || row.result === auditResult;

    return matchesDate && matchesType && matchesResponsible && matchesAction && matchesDevice && matchesResult;
  }), [auditRows, auditDateRange, auditDeviceType, auditResponsible, auditAction, auditDevice, auditResult]);

  const auditColumnFilters = (field: 'datetime' | 'user' | 'action' | 'device' | 'deviceType' | 'result') => (
    Array.from(new Set(auditRows.map((row) => row[field]))).map((value) => ({ text: value, value }))
  );

  const auditColumns = [
    {
      title: 'Дата/время',
      dataIndex: 'datetime',
      filters: auditColumnFilters('datetime'),
      onFilter: (value: React.Key | boolean, record: any) => record.datetime === value,
    },
    {
      title: 'Ответственный',
      dataIndex: 'user',
      filters: auditColumnFilters('user'),
      onFilter: (value: React.Key | boolean, record: any) => record.user === value,
    },
    {
      title: 'Действие',
      dataIndex: 'action',
      filters: auditColumnFilters('action'),
      onFilter: (value: React.Key | boolean, record: any) => record.action === value,
    },
    {
      title: 'Устройство',
      dataIndex: 'device',
      filters: auditColumnFilters('device'),
      onFilter: (value: React.Key | boolean, record: any) => record.device === value,
    },
    {
      title: 'Тип устройства',
      dataIndex: 'deviceType',
      filters: auditColumnFilters('deviceType'),
      onFilter: (value: React.Key | boolean, record: any) => record.deviceType === value,
    },
    {
      title: 'Результат',
      dataIndex: 'result',
      filters: auditColumnFilters('result'),
      onFilter: (value: React.Key | boolean, record: any) => record.result === value,
      render: (value: string) => <Tag color={value === 'Успех' ? 'success' : 'error'}>{value}</Tag>,
    },
  ];

  const renderDiffLine = (line: DiffLine, index: number, panel: 'previous' | 'current') => {
    const background = line.status === 'added' ? '#d9f7be' : line.status === 'removed' ? '#ffd6d6' : 'transparent';
    const color = line.status === 'added' ? '#135200' : line.status === 'removed' ? '#820014' : '#262626';
    const canTransfer = panel === 'previous' && line.status === 'removed';

    return (
      <div
        key={`${panel}-${line.status}-${index}-${line.value}`}
        style={{
          alignItems: 'center',
          background,
          color,
          display: 'flex',
          gap: 8,
          minHeight: 28,
          padding: '2px 8px',
        }}
      >
        <span style={{ flex: 1, fontFamily: 'monospace', whiteSpace: 'pre-wrap' }}>
          <Text strong>{line.option}</Text>: {line.value || ' '}
        </span>
        {canTransfer && (
          <Tooltip title="Перенести строку в текущую конфигурацию">
            <Button
              aria-label="Перенести строку вправо"
              icon={<ArrowRightOutlined />}
              onClick={() => handleTransferLine(line, index)}
              size="small"
              type="text"
            />
          </Tooltip>
        )}
      </div>
    );
  };

  const openConfigModalByType = (device?: Device) => {
    const typeCode = device?.typeCode ?? selectedDevice?.typeCode;
    if (typeCode === 2) return setCiscoModalOpen(true);
    if (typeCode === 1) return setMfuModalOpen(true);
    if (typeCode === 0) return setWindowsModalOpen(true);
    if (typeCode === 3) return setLinuxModalOpen(true);
    message.info('Выберите устройство из списка');
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
        const existingIps = new Set(prev.map(d => d.ips));
        return [
          ...prev,
          ...newDevices.filter(d => !existingIps.has(d.ips))
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
      <Tabs
        defaultActiveKey='devices'
        items={[
          {
            key: 'devices',
            label: 'Список устройств',
            children: (
              <>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                <h2>
                  Устройства:
                  {type ? ` ${deviceTypeInfo[routeTypeMap[type]]?.name || routeTypeMap[type] || type}` : ' Всего'} 
                  <span style={{ marginLeft: 16, fontSize: '0.9em', color: '#666' }}>
                    ({filteredDevices.length} из {devices.length} шт.)
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

              <Card style={{ marginBottom: 16 }}>
                <Search
                  allowClear
                  placeholder="Поиск по имени, IP, группе, ОС или типу устройства"
                  value={deviceSearch}
                  onChange={(event) => setDeviceSearch(event.target.value)}
                  style={{ maxWidth: 520 }}
                />
              </Card>

              {loading ? (
                <div style={{ textAlign: 'center', padding: 40 }}>Загрузка...</div>
              ) : filteredDevices.length === 0 ? (
                <Empty description="Нет устройств для отображения" />
              ) : (
                <Card>
                  <Space wrap style={{ marginBottom: 16 }}>
                    <Text strong>Выбрано устройств: {selectedDeviceIds.length}</Text>
                    <Button
                      disabled={selectedDeviceIds.length === 0}
                      icon={<FileDoneOutlined />}
                      type="primary"
                      onClick={() => message.success(`Массовое применение настроек запущено для ${selectedDeviceIds.length} устройств`)}
                    >
                      Применить настройки
                    </Button>
                    <Button disabled={selectedDeviceIds.length === 0} onClick={() => setSelectedDeviceIds([])}>
                      Сбросить выбор
                    </Button>
                  </Space>
                  <Table
                    rowKey="id"
                    rowSelection={{
                      selectedRowKeys: selectedDeviceIds,
                      onChange: setSelectedDeviceIds,
                    }}
                    columns={deviceColumns}
                    dataSource={filteredDevices}
                    pagination={{
                      defaultPageSize: 15,
                      pageSizeOptions: [15, 30, 50],
                      locale: { items_per_page: '' },
                      position: ['bottomRight'],
                      showSizeChanger: true,
                      showTotal: (total, range) => `${range[0]}-${range[1]} из ${total} устройств`,
                    }}
                  />
                </Card>
              )}
              </>
            )
          },
          {
            key: 'config',
            label: 'Просмотр и сравнение конфигурации',
            children: (
              <Card title="Работа с конфигурациями устройства">
                <Tabs
                  items={[
                    {
                      key: 'current',
                      label: 'Текущая',
                      children: (
                        <Card size="small" styles={{ body: { padding: 0 } }}>
                          <div style={{ background: '#111', borderRadius: 8, overflow: 'hidden' }}>
                            {currentConfigOptions.map((line, index) => (
                              <div key={`${line.option}-${index}`} style={{ color: '#7CFC00', fontFamily: 'monospace', padding: '4px 12px' }}>
                                <Text strong style={{ color: '#7CFC00' }}>{line.option}</Text>: {line.value}
                              </div>
                            ))}
                          </div>
                        </Card>
                      ),
                    },
                    {
                      key: 'history',
                      label: 'История',
                      children: (
                        <Space direction="vertical" style={{ width: '100%' }}>
                          <Paragraph>Версии конфигурации за последние 30 дней.</Paragraph>
                          <Table
                            rowKey={(record) => record.id || record.versionNumber}
                            columns={[
                              { title: '№', dataIndex: 'id', key: 'id' },
                              { title: 'Версия', dataIndex: 'versionNumber', key: 'versionNumber' },
                              {
                                title: 'Дата применения',
                                dataIndex: 'appliedAt',
                                key: 'appliedAt',
                                render: (value?: string) => value ? new Date(value).toLocaleString('ru-RU') : 'без даты',
                              },
                              {
                                title: 'Тип конфигурации',
                                dataIndex: 'configType',
                                key: 'configType',
                                render: (value: number) => <Tag color="blue">{value}</Tag>,
                              },
                              {
                                title: 'Откат',
                                dataIndex: 'rollbackAvailable',
                                key: 'rollbackAvailable',
                                render: (value: boolean) => <Tag color={value ? 'success' : 'default'}>{value ? 'доступен' : 'недоступен'}</Tag>,
                              },
                              {
                                title: 'Действия',
                                key: 'actions',
                                render: (_, record: ConfigVersion) => (
                                  <Button
                                    type={record.id === selectedConfigVersionId ? 'primary' : 'default'}
                                    onClick={() => {
                                      setSelectedConfigVersionId(record.id!);
                                      setTransferredLines({});
                                    }}
                                  >
                                    Открыть в сравнении
                                  </Button>
                                ),
                              },
                            ]}
                            dataSource={configVersions}
                            pagination={false}
                          />
                        </Space>
                      ),
                    },
                    {
                      key: 'compare',
                      label: 'Сравнение',
                      children: (
                        <Space direction="vertical" style={{ width: '100%' }} size="large">
                          <Space wrap>
                            <Text strong>Конфигурация из БД:</Text>
                            <Select
                              value={selectedConfigVersionId}
                              style={{ minWidth: 360 }}
                              onChange={(value) => {
                                setSelectedConfigVersionId(value);
                                setTransferredLines({});
                              }}
                              options={configVersions.map((config) => ({
                                value: config.id!,
                                label: `№ ${config.id} · версия ${config.versionNumber} · ${config.appliedAt ? new Date(config.appliedAt).toLocaleString('ru-RU') : 'без даты'}`,
                              }))}
                            />
                          </Space>

                          <Row gutter={[16, 16]}>
                            <Col xs={24} lg={12}>
                              <Card size="small" title="Предыдущая конфигурация" styles={{ body: { padding: 0 } }}>
                                <div style={{ background: '#fff', borderRadius: 8, overflow: 'hidden' }}>
                                  {diffPanels.previous.map((line, index) => renderDiffLine(line, index, 'previous'))}
                                </div>
                              </Card>
                            </Col>
                            <Col xs={24} lg={12}>
                              <Card size="small" title="Текущая конфигурация" styles={{ body: { padding: 0 } }}>
                                <div style={{ background: '#fff', borderRadius: 8, overflow: 'hidden' }}>
                                  {diffPanels.current.map((line, index) => renderDiffLine(line, index, 'current'))}
                                </div>
                              </Card>
                            </Col>
                          </Row>

                          <Space wrap>
                            <Button type="primary" icon={<FileDoneOutlined />}>Применить</Button>
                            <Button icon={<ReloadOutlined />}>Откатить</Button>
                            <Button icon={<DownloadOutlined />}>Выгрузить</Button>
                          </Space>
                        </Space>
                      ),
                    },
                    {
                      key: 'templates',
                      label: 'Шаблоны',
                      children: (
                        <Card title="Пример шаблона baseline" size="small">
                          <Space direction="vertical" style={{ width: '100%' }}>
                            <Paragraph>Шаблон можно применить к выбранным устройствам из списка устройств.</Paragraph>
                            {[
                              { option: 'hostname.prefix', value: 'office-' },
                              { option: 'interface.uplink.description', value: 'Managed by Config Manager' },
                              { option: 'logging.buffered', value: '8192' },
                              { option: 'snmp.location', value: 'Main Office' },
                            ].map((item) => (
                              <div key={item.option} style={{ background: '#f8fafc', borderRadius: 6, padding: '8px 12px' }}>
                                <Text strong>{item.option}</Text>: {item.value}
                              </div>
                            ))}
                            <Button icon={<FileDoneOutlined />} type="primary">Применить шаблон</Button>
                          </Space>
                        </Card>
                      ),
                    },
                  ]}
                />
              </Card>
            ),
          },
          {
            key: 'terminal',
            label: 'Встроенный терминал',
            children: (
              <Card title="Административный терминал" extra={<Tag color={terminalConnected ? 'green' : 'orange'}>{terminalConnected ? 'Подключено' : 'Нет подключения'}</Tag>}>
                <Space direction="vertical" style={{ width: '100%' }}>
                  <AutoComplete style={{ width: '100%' }} options={commandSuggestions.map((value) => ({ value }))} value={terminalCommand} onChange={setTerminalCommand} placeholder="Введите команду" />
                  <pre style={{ background: '#101820', color: '#e6f7ff', minHeight: 180, padding: 12 }}>$ {terminalCommand || 'show running-config'}
{terminalConnected ? 'Вывод команды...' : 'Терминал отключен'}</pre>
                  <Space wrap>
                    <Button type="primary" icon={<CodeOutlined />} onClick={() => { setTerminalConnected(true); setShowSSH(true); }}>Открыть xterm.js</Button>
                    <Button icon={<FileSearchOutlined />}>Захватить конфигурацию</Button>
                    <Button danger disabled={!terminalConnected} onClick={() => { setTerminalConnected(false); setShowSSH(false); message.success('Терминал отключен'); }}>Отключиться</Button>
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
                  <DatePicker.RangePicker onChange={(value) => setAuditDateRange(value as [any, any] | null)} />
                  <Select allowClear placeholder="Ответственный" style={{ width: 180 }} onChange={setAuditResponsible} options={[{ value: 'admin', label: 'admin' }, { value: 'operator', label: 'operator' }, { value: 'network-engineer', label: 'network-engineer' }]} />
                  <Select allowClear placeholder="Тип устройства" style={{ width: 180 }} onChange={setAuditDeviceType} options={Object.entries(deviceTypeInfo).map(([k, v]) => ({ value: k, label: v.name }))} />
                  <Select allowClear placeholder="Действие" style={{ width: 180 }} onChange={setAuditAction} options={auditColumnFilters('action').map(({ value }) => ({ value, label: value }))} />
                  <Select allowClear placeholder="Устройство" style={{ width: 180 }} onChange={setAuditDevice} options={auditColumnFilters('device').map(({ value }) => ({ value, label: value }))} />
                  <Select allowClear placeholder="Результат" style={{ width: 180 }} onChange={setAuditResult} options={auditColumnFilters('result').map(({ value }) => ({ value, label: value }))} />
                </Space>
                <Table columns={auditColumns} dataSource={filteredAuditRows} />
              </Card>
            ),
          },
        ]}
      />

      {/* Модалки */}
      <SSHClient
        open={showSSH}
        onClose={() => setShowSSH(false)}
        device={{
          hostname: selectedDevice?.hostname || '',
          ip: selectedDevice?.ips[0] || '',
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
      <ConfigLinuxModal open={linuxModalOpen} onClose={() => setLinuxModalOpen(false)} hostname={selectedDevice?.hostname} />
      <ConfigWindowsModal open={windowsModalOpen} onClose={() => setWindowsModalOpen(false)} hostname={selectedDevice?.hostname} />
      <ConfigMFUModal open={mfuModalOpen} onClose={() => setMfuModalOpen(false)} hostname={selectedDevice?.hostname} />
      <ConfigCiscoModal open={ciscoModalOpen} onClose={() => setCiscoModalOpen(false)} hostname={selectedDevice?.hostname} deviceId={selectedDevice?.id} />
    </>
  );
}


