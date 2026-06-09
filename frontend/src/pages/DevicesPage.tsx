import React, { useEffect, useMemo, useState } from 'react';
import { Card, Table, Space, Button, Tag, Empty, message, Tabs, AutoComplete, DatePicker, Select, Typography, Input, Row, Col, Progress, Tooltip, Modal as AntModal } from 'antd';
import { ExclamationCircleOutlined, EyeOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { devicesApi } from '../api/devicesApi';
import { configApi } from '../api/configApi';
import { eventApi } from '../api/eventApi';
import ResolveDriftModal from '../components/ResolveDriftModal';
import type { ConfigVersion, Device, EventLog } from '../types';
import { eventActionLabel, eventResult, eventResultColor } from '../utils/eventLabels';
import { getErrorMessage } from '../utils/errorMessage';
import SSHClient from '../components/DeviceTerminal';
import { ArrowRightOutlined, CodeOutlined, ScanOutlined, PlusCircleOutlined, DeleteOutlined, ReloadOutlined, DownloadOutlined, FileDoneOutlined, FileSearchOutlined, EditOutlined, WarningOutlined, ClockCircleOutlined } from "@ant-design/icons"
import { ScanDeviceModal } from '../components/ScanDeviceModal';
import { AddDeviceModal } from '../components/AddDeviceModal';
import { ScanProgress } from '../components/ScanProgress';
import { ColumnsType } from 'antd/es/table';
import ConfigLinuxModal from '../components/ConfigLinuxModal';
import ConfigWindowsModal from '../components/ConfigWindowsModal';
import ConfigMFUModal from '../components/ConfigMFUModal';
import ConfigCiscoModal from '../components/ConfigCiscoModal';
import DeviceTerminal from '../components/DeviceTerminal';
import ConfigDiffViewer from '../components/ConfigDiffViewer';
import ConfigFormViewer from '../components/ConfigFormViewer';

const { Text, Paragraph } = Typography;
const { confirm } = AntModal;
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

type DeviceRuntimeStatus = 'online' | 'offline' | 'error' | 'checking';
const commandSuggestions = ['show running-config', 'show version', 'ipconfig /all', 'hostname', 'reload'];

const parseConfigLine = (line: string) => {
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
    previous: previousLines.map((line) => ({ ...parseConfigLine(line), status: currentLines.includes(line) ? 'unchanged' : 'removed' })),
    current: currentLines.map((line) => ({ ...parseConfigLine(line), status: previousLines.includes(line) ? 'unchanged' : 'added' })),
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
  const [scanStats, setScanStats] = useState<{ scanned: number; total: number }>({ scanned: 0, total: 0 });
  const [terminalCommand, setTerminalCommand] = useState('');
  const [terminalConnected, setTerminalConnected] = useState(false);
  const [deviceSearch, setDeviceSearch] = useState('');
  const [selectedDeviceIds, setSelectedDeviceIds] = useState<React.Key[]>([]);
  const [configVersions, setConfigVersions] = useState<ConfigVersion[]>([]);
  const [selectedConfigVersionId, setSelectedConfigVersionId] = useState<number>();
  const [compareVersionId, setCompareVersionId] = useState<number>();
  const [deleting, setDeleting] = useState(false);
  const [activeTabKey, setActiveTabKey] = useState<string>('devices');

  // Журнал событий устройств текущей вкладки
  const [deviceLogs, setDeviceLogs] = useState<EventLog[]>([]);
  const [auditLoading, setAuditLoading] = useState(false);

  // id устройства, для которого идёт инвентаризация
  const [inventoryLoadingId, setInventoryLoadingId] = useState<number | null>(null);

  // Реальная доступность устройств (ping) и счётчики отложенного применения
  const [statuses, setStatuses] = useState<Record<number, boolean>>({});
  const [scheduledByDevice, setScheduledByDevice] = useState<Record<number, number>>({});
  // Устройство, для которого открыта модалка разрешения расхождения (drift)
  const [driftDevice, setDriftDevice] = useState<Device | null>(null);

  // Модалки конфигурации
  const [linuxModalOpen, setLinuxModalOpen] = useState(false);
  const [windowsModalOpen, setWindowsModalOpen] = useState(false);
  const [mfuModalOpen, setMfuModalOpen] = useState(false);
  const [ciscoModalOpen, setCiscoModalOpen] = useState(false);

  // Загрузка устройств
  useEffect(() => {
    loadDevices();
  }, [type]);

  // Реальный статус (ping) и очередь отложенного применения — с периодическим опросом
  const loadStatuses = async () => {
    try {
      setStatuses(await devicesApi.getStatuses());
    } catch { /* статус не критичен — оставляем «проверка» */ }
  };
  const loadScheduled = async () => {
    try {
      const list = await configApi.getScheduled();
      const counts: Record<number, number> = {};
      list.forEach((s) => {
        if (s.status === 'PENDING') counts[s.deviceId] = (counts[s.deviceId] || 0) + 1;
      });
      setScheduledByDevice(counts);
    } catch { /* очередь не критична */ }
  };
  useEffect(() => {
    loadStatuses();
    loadScheduled();
    const timer = setInterval(() => { loadStatuses(); loadScheduled(); }, 20000);
    return () => clearInterval(timer);
  }, []);

  // Загрузка версий конфигурации при выборе устройства
  useEffect(() => {
    if (selectedDevice?.id) {
      loadConfigVersions(selectedDevice.id);
    } else {
      setConfigVersions([]);
      setSelectedConfigVersionId(undefined);
      setCompareVersionId(undefined);
    }
  }, [selectedDevice]);

  const loadDevices = async () => {
    setLoading(true);
    try {
      const res = await devicesApi.getAll();
      let list = res.data;
      
      list = list.map(device => {
        if (!device.operatingSystem && device.typeCode === 0) {
          return { ...device, operatingSystem: 'windows', model: device.model || 'OptiPlex 7090' };
        }
        if (!device.operatingSystem && device.typeCode === 3) {
          return { ...device, operatingSystem: 'linux', model: device.model || 'VMware VM' };
        }
        if (!device.manufacturer && device.typeCode === 1) {
          return { ...device, manufacturer: 'HP', model: device.model || 'LaserJet Pro M428fdw' };
        }
        return device;
      });
      
      const mappedType = type ? routeTypeMap[type] : undefined;
      if (mappedType) {
        const expectedCode = deviceTypeInfo[mappedType].code;
        list = list.filter((device) => device.type === mappedType || device.typeCode === expectedCode);
      }
      setDevices(list);
    } catch (err) {
      message.error(getErrorMessage(err, 'Ошибка загрузки устройств'));
      setDevices([]);
    } finally {
      setLoading(false);
    }
  };

  // id устройства -> hostname, для отображения в журнале
  const deviceNameById = useMemo(() => {
    const map = new Map<number, string>();
    devices.forEach((d) => { if (d.id != null) map.set(d.id, d.hostname); });
    return map;
  }, [devices]);

  // Загрузка событий для устройств текущей вкладки
  const loadDeviceAudit = async () => {
    const ids = devices.map((d) => d.id).filter((id): id is number => id != null);
    if (ids.length === 0) {
      setDeviceLogs([]);
      return;
    }
    setAuditLoading(true);
    try {
      const res = await eventApi.getLogs({ deviceIds: ids });
      setDeviceLogs(res.data);
    } catch (err) {
      console.error('Не удалось загрузить журнал событий:', err);
      message.error(getErrorMessage(err, 'Не удалось загрузить журнал событий'));
      setDeviceLogs([]);
    } finally {
      setAuditLoading(false);
    }
  };

  // Подгружаем журнал при открытии вкладки и при смене набора устройств
  useEffect(() => {
    if (activeTabKey === 'audit') {
      loadDeviceAudit();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTabKey, devices]);

  const handleViewConfig = async (device: Device) => {
    setSelectedDevice(device);
    await loadConfigVersions(device.id!);
    setActiveTabKey('config');
  };

  const loadConfigVersions = async (deviceId: number) => {
    try {
      const res = await devicesApi.getHistory(deviceId);
      const data = res.data as any;
      const rawHistory: any[] = Array.isArray(data)
        ? data
        : Array.isArray(data?.history) ? data.history : [];

      const versions: (ConfigVersion & { configData?: Record<string, any>; parentVersionId?: number })[] =
        rawHistory.map((entry: any) => ({
          id: entry.versionId ?? entry.id,
          deviceId,
          configType: 0,
          versionNumber: entry.versionNum ?? entry.versionNumber ?? 0,
          appliedAt: entry.createdAt,
          oldConfigJson: entry.oldConfigJson ?? '',
          newConfig: entry.newConfig ?? JSON.stringify(entry.configData ?? {}),
          diffHash: entry.checksum,
          rollbackAvailable: !!entry.parentVersionId,
          configData: entry.configData ?? null,
          parentVersionId: entry.parentVersionId ?? null,
        }));
      setConfigVersions(versions);
      if (versions.length > 0) {
        setSelectedConfigVersionId(versions[0].id);
        if (versions.length > 1) setCompareVersionId(versions[1].id);
      } else {
        setSelectedConfigVersionId(undefined);
        setCompareVersionId(undefined);
      }
    } catch (err) {
      message.error(getErrorMessage(err, 'Ошибка загрузки истории конфигураций'));
      setConfigVersions([]);
    }
  };


const handleDelete = async (id?: number, hostname?: string) => {
  if (!id) return;
  
  confirm({
    title: 'Удаление устройства',
    icon: <ExclamationCircleOutlined />,
    content: `Вы уверены, что хотите удалить устройство "${hostname}"? Это действие нельзя отменить.`,
    okText: 'Да, удалить',
    okType: 'danger',
    cancelText: 'Отмена',
    onOk: async () => {
      try {
        await devicesApi.delete(id);
        message.success(`Устройство "${hostname}" успешно удалено`);
        setDevices((prev) => prev.filter((d) => d.id !== id));
        
        if (selectedDevice?.id === id) {
          setSelectedDevice(null);
        }

        setSelectedDeviceIds(prev => prev.filter(selectedId => selectedId !== id));
      } catch (err: any) {
        console.error('Ошибка удаления:', err);
        message.error(err.response?.data?.message || 'Ошибка удаления устройства');
      }
    },
  });
};

const handleBulkDelete = async () => {
  if (selectedDeviceIds.length === 0) return;
  
  const devicesToDelete = devices.filter(d => selectedDeviceIds.includes(d.id!));
  const deviceNames = devicesToDelete.map(d => d.hostname).join(', ');
  
  confirm({
    title: 'Массовое удаление устройств',
    icon: <ExclamationCircleOutlined />,
    content: (
      <div>
        <p>Вы уверены, что хотите удалить следующие устройства?</p>
        <p><strong>{selectedDeviceIds.length} устройств(а):</strong></p>
        <p style={{ fontSize: '12px', color: '#666', maxHeight: '200px', overflow: 'auto' }}>
          {deviceNames}
        </p>
        <p style={{ color: 'red', marginTop: '10px' }}>Это действие нельзя отменить!</p>
      </div>
    ),
    okText: 'Да, удалить все',
    okType: 'danger',
    cancelText: 'Отмена',
    onOk: async () => {
      setDeleting(true);
      try {

        const response = await devicesApi.bulkDelete(selectedDeviceIds as number[]);
        
        if (response.data.failCount === 0) {
          message.success(`Успешно удалено ${response.data.successCount} устройств`);
        } else {
          message.warning(
            `Удалено ${response.data.successCount} устройств. Ошибок: ${response.data.failCount}`
          );

          response.data.errors.forEach((error: any) => {
            console.error(`Ошибка удаления устройства ${error.id}: ${error.error}`);
            message.error(`Не удалось удалить устройство ID: ${error.id} - ${error.error}`);
          });
        }

        setDevices((prev) => prev.filter((d) => !selectedDeviceIds.includes(d.id!)));
        setSelectedDeviceIds([]);

        if (selectedDevice && selectedDeviceIds.includes(selectedDevice.id!)) {
          setSelectedDevice(null);
        }
      } catch (err: any) {
        console.error('Ошибка массового удаления:', err);
        message.error(err.response?.data?.message || 'Ошибка при массовом удалении устройств');
      } finally {
        setDeleting(false);
      }
    },
  });
};

  const runtimeStatus = (device: Device): DeviceRuntimeStatus => {
    if (device.id == null) return 'checking';
    const online = statuses[device.id];
    if (online === undefined) return 'checking'; // статус ещё не загружен
    return online ? 'online' : 'offline';
  };

  const statusColor = (status: DeviceRuntimeStatus) =>
    status === 'online' ? 'success' : status === 'offline' ? 'default' : status === 'checking' ? 'processing' : 'error';
  const statusText = (status: DeviceRuntimeStatus) =>
    status === 'online' ? 'Онлайн' : status === 'offline' ? 'Оффлайн' : status === 'checking' ? 'Проверка…' : 'Ошибка';

  // Прогресс применения – получаем из API по deviceId (можно добавить эндпоинт)
  const getActionProgress = (device: Device) => 0; // пока заглушка

  // Обновленные колонки таблицы с отображением ОС и производителя
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
      title: 'ОС / Производитель',
      key: 'osOrManufacturer',
      render: (_, record) => {
        // Для ПК (typeCode 0) и VM (typeCode 3) показываем ОС
        if ((record.typeCode === 0 || record.typeCode === 3 || record.osVersion === 'linux') && record.operatingSystem) {
          const osMap = {
            linux: <Tag icon={<CodeOutlined />} color="blue">Linux</Tag>,
            windows: <Tag icon={<CodeOutlined />} color="cyan">Windows</Tag>
          };
          return osMap[record.operatingSystem as 'linux' | 'windows'] || record.operatingSystem;
        }
        // Для МФУ (typeCode 1) показываем производителя
        if (record.typeCode === 1 && record.manufacturer) {
          const manufacturerColors: Record<string, string> = {
            'HP': 'green',
            'Canon': 'geekblue',
            'Xerox': 'orange',
            'Kyocera': 'purple',
            'Brother': 'cyan',
            'Epson': 'magenta',
            'Samsung': 'blue',
            'Ricoh': 'gold',
            'Sharp': 'lime',
            'Konica Minolta': 'red'
          };
          return <Tag color={manufacturerColors[record.manufacturer] || 'green'}>{record.manufacturer}</Tag>;
        }
        // Для Cisco (typeCode 2) показываем "Cisco"
        if (record.typeCode === 2) {
          return <Tag color="orange">Cisco</Tag>;
        }
        return <Text type="secondary">—</Text>;
      },
    },
    {
      title: 'Модель',
      key: 'model',
      render: (_, record) => record.model ? <Text>{record.model}</Text> : <Text type="secondary">—</Text>,
    },
    {
      title: 'Статус',
      key: 'status',
      render: (_, record) => {
        const status = runtimeStatus(record);
        const pending = record.id != null ? scheduledByDevice[record.id] : 0;
        return (
          <Space direction="vertical" size={2} align="start">
            <Tag color={statusColor(status)}>{statusText(status)}</Tag>
            {pending ? (
              <Tooltip title="Применение отложено: устройство было офлайн, применится автоматически при появлении в сети">
                <Tag icon={<ClockCircleOutlined />} color="processing">
                  Запланировано{pending > 1 ? ` ×${pending}` : ''}
                </Tag>
              </Tooltip>
            ) : null}
            {record.configDrift ? (
              <Tooltip title="Фактическая конфигурация разошлась с сохранённой. Нажмите, чтобы разрешить.">
                <Tag icon={<WarningOutlined />} color="warning" style={{ cursor: 'pointer' }}
                  onClick={() => setDriftDevice(record)}>
                  Расхождение
                </Tag>
              </Tooltip>
            ) : null}
          </Space>
        );
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
          <Progress type="circle" size={44} percent={progress} status={progress >= 100 ? 'success' : 'normal'} />
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
            <Button icon={<EditOutlined/>} onClick={() => { setSelectedDevice(record); openConfigModalByType(record); }} />
          </Tooltip>
          <Tooltip title="Терминал">
            <Button icon={<CodeOutlined />} onClick={() => { setSelectedDevice(record); setShowSSH(true); }} />
          </Tooltip>
          <Tooltip title="Просмотр конфигурации">
            <Button icon={<EyeOutlined />} onClick={() => handleViewConfig(record)} />
          </Tooltip>
          <Tooltip title="Инвентаризация">
            <Button
              icon={<ScanOutlined />}
              loading={inventoryLoadingId === record.id}
              onClick={() => handleInventory(record)}
            />
          </Tooltip>
          <Tooltip title="Удалить">
            <Button danger icon={<DeleteOutlined />} onClick={() => handleDelete(record.id, record.hostname)} />
          </Tooltip>
        </Space>
      ),
    },
  ];

  const auditRows = useMemo(() => (
    deviceLogs
      .map((log) => ({
        key: log.id ?? `${log.aggregateId}-${log.createdAt}`,
        timestamp: log.createdAt ? new Date(log.createdAt).getTime() : 0,
        datetime: log.createdAt ? new Date(log.createdAt).toLocaleString('ru-RU') : '—',
        action: eventActionLabel(log.eventType),
        deviceName: log.aggregateId != null
          ? (deviceNameById.get(log.aggregateId) || `ID ${log.aggregateId}`)
          : '—',
        user: log.userName || (log.userId != null ? `ID ${log.userId}` : 'system'),
        result: eventResult(log.eventType),
        payload: log.payload,
        metadata: log.metadata,
      }))
      .sort((a, b) => b.timestamp - a.timestamp)
  ), [deviceLogs, deviceNameById]);

  const exportAuditCsv = () => {
    if (auditRows.length === 0) {
      message.info('Нет событий для экспорта');
      return;
    }
    const header = ['Дата/время', 'Действие', 'Устройство', 'Пользователь', 'Результат'];
    const escape = (v: string) => `"${String(v).replace(/"/g, '""')}"`;
    const csv = [
      header.join(';'),
      ...auditRows.map((r) => [r.datetime, r.action, r.deviceName, r.user, r.result].map(escape).join(';')),
    ].join('\n');
    const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `audit-${type || 'all'}-${dayjs().format('YYYY-MM-DD')}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  };

  const renderAuditTab = () => {
    const columns: ColumnsType<typeof auditRows[number]> = [
      { title: 'Дата/время', dataIndex: 'datetime', width: 180, sorter: (a, b) => a.timestamp - b.timestamp, defaultSortOrder: 'descend' },
      {
        title: 'Действие',
        dataIndex: 'action',
        filters: Array.from(new Set(auditRows.map((r) => r.action))).map((v) => ({ text: v, value: v })),
        onFilter: (value, record) => record.action === value,
        render: (value: string) => <Tag color="blue">{value}</Tag>,
      },
      {
        title: 'Устройство',
        dataIndex: 'deviceName',
        filters: Array.from(new Set(auditRows.map((r) => r.deviceName))).map((v) => ({ text: v, value: v })),
        onFilter: (value, record) => record.deviceName === value,
      },
      { title: 'Пользователь', dataIndex: 'user', width: 160 },
      {
        title: 'Результат',
        dataIndex: 'result',
        width: 120,
        filters: [{ text: 'Успех', value: 'Успех' }, { text: 'Ошибка', value: 'Ошибка' }],
        onFilter: (value, record) => record.result === value,
        render: (value: 'Успех' | 'Ошибка') => <Tag color={eventResultColor(value)}>{value}</Tag>,
      },
    ];

    return (
      <Card
        title="Журнал событий устройств"
        extra={(
          <Space>
            <Button icon={<ReloadOutlined />} onClick={loadDeviceAudit} loading={auditLoading}>Обновить</Button>
            <Button icon={<DownloadOutlined />} onClick={exportAuditCsv}>CSV</Button>
          </Space>
        )}
      >
        {!auditLoading && auditRows.length === 0 ? (
          <Empty description="Пока нет событий для устройств этой вкладки" />
        ) : (
          <Table
            rowKey="key"
            loading={auditLoading}
            columns={columns}
            dataSource={auditRows}
            pagination={{ pageSize: 15, showTotal: (total) => `Всего событий: ${total}` }}
            expandable={{
              rowExpandable: (record) => Boolean(record.payload || record.metadata),
              expandedRowRender: (record) => (
                <Row gutter={16}>
                  <Col span={12}>
                    <Text strong>Было:</Text>
                    <pre style={{ background: '#f5f5f5', padding: 8, margin: '4px 0 0', maxHeight: 240, overflow: 'auto' }}>
                      {record.payload ? JSON.stringify(record.payload, null, 2) : '—'}
                    </pre>
                  </Col>
                  <Col span={12}>
                    <Text strong>Стало:</Text>
                    <pre style={{ background: '#f5f5f5', padding: 8, margin: '4px 0 0', maxHeight: 240, overflow: 'auto' }}>
                      {record.metadata ? JSON.stringify(record.metadata, null, 2) : '—'}
                    </pre>
                  </Col>
                </Row>
              ),
            }}
          />
        )}
      </Card>
    );
  };

  const renderConfigTab = () => {
    if (!selectedDevice) {
      // Показываем список устройств того же типа (или всех, если type не задан)
      const filteredByType = type
        ? devices.filter(d => d.type === routeTypeMap[type] || d.typeCode === deviceTypeInfo[routeTypeMap[type]]?.code)
        : devices;
      return (
        <Card title="Выберите устройство для просмотра конфигурации">
        {filteredByType.length === 0 ? (
          <Empty description="Нет устройств данного типа" />
        ) : (
          <Table
            rowKey="id"
            columns={[
              { title: 'Имя устройства', dataIndex: 'hostname' },
              { title: 'IP-адрес', render: (_, rec) => rec.ips?.[0] || '—' },
              { title: 'Тип', render: (_, rec) => <Tag color={deviceTypeInfo[rec.type]?.color}>{deviceTypeInfo[rec.type]?.name}</Tag> },
              { title: '', render: (_, rec) => <Button type="primary" onClick={() => handleViewConfig(rec)}>Выбрать</Button> },
            ]}
            dataSource={filteredByType}
            pagination={{ pageSize: 10 }}
          />
        )}
      </Card>
    );
  }

    // Если устройство выбрано – показываем существующий интерфейс работы с конфигурацией
      return (
    <Card
      title={`Конфигурация устройства: ${selectedDevice.hostname}`}
      extra={<Button onClick={() => setSelectedDevice(null)}>Назад к списку</Button>}
    >
      <Tabs
        items={[
          {
            key: 'current',
            label: 'Текущая',
            children: (
              <ConfigFormViewer
                config={diffRightConfig}
                deviceType={selectedDevice.type}
                deviceId={selectedDevice.id!}
                hostname={selectedDevice.hostname}
                onApplied={() => loadConfigVersions(selectedDevice.id!)}
              />
            ),
          },
          {
            key: 'history',
            label: 'История',
            children: (
              <Space direction="vertical" style={{ width: '100%' }}>
                <Paragraph>Версии конфигурации устройства</Paragraph>
                <Table
                  rowKey="id"
                  columns={[
                    { title: 'ID', dataIndex: 'id' },
                    { title: 'Версия', dataIndex: 'versionNumber' },
                    { title: 'Дата применения', dataIndex: 'appliedAt', render: (v) => v ? new Date(v).toLocaleString() : '—' },
                    { title: 'Откат', dataIndex: 'rollbackAvailable', render: (v) => <Tag color={v ? 'success' : 'default'}>{v ? 'доступен' : 'нет'}</Tag> },
                    {
                      title: 'Действия',
                      render: (_, rec) => (
                        <Button
                          type={rec.id === selectedConfigVersionId ? 'primary' : 'default'}
                          onClick={() => { setCompareVersionId(rec.id); setActiveTabKey('compare'); }}
                        >
                          Сравнить
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
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                {/* Селекторы версий */}
                <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center', background: '#fafafa', padding: '10px 12px', borderRadius: 6, border: '1px solid #e8e8e8' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <Text strong style={{ color: '#a61d24' }}>◀ Сравниваемая:</Text>
                    <Select
                      placeholder="Выберите версию для сравнения"
                      value={compareVersionId}
                      style={{ minWidth: 300 }}
                      allowClear
                      onChange={setCompareVersionId}
                      options={(configVersions as any[]).map((v: any) => ({
                        value: v.id,
                        label: `v${v.versionNumber} · ${v.appliedAt ? new Date(v.appliedAt).toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' }) : 'без даты'} · ${v.diffHash?.slice(0,8) ?? ''}`
                      }))}
                    />
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <Text strong style={{ color: '#237804' }}>▶ Базовая:</Text>
                    <Select
                      placeholder="Выберите базовую версию"
                      value={selectedConfigVersionId}
                      style={{ minWidth: 300 }}
                      allowClear
                      onChange={setSelectedConfigVersionId}
                      options={(configVersions as any[]).map((v: any) => ({
                        value: v.id,
                        label: `v${v.versionNumber} · ${v.appliedAt ? new Date(v.appliedAt).toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' }) : 'без даты'} · ${v.diffHash?.slice(0,8) ?? ''}`
                      }))}
                    />
                  </div>
                </div>

                {/* Diff viewer */}
                {(compareVersionId || selectedConfigVersionId) ? (
                  <ConfigDiffViewer
                    leftConfig={diffLeftConfig}
                    rightConfig={diffRightConfig}
                    leftLabel={compareVersionId
                      ? `v${(configVersions as any[]).find((v: any) => v.id === compareVersionId)?.versionNumber ?? '?'} (сравниваемая)`
                      : '—'}
                    rightLabel={selectedConfigVersionId
                      ? `v${(configVersions as any[]).find((v: any) => v.id === selectedConfigVersionId)?.versionNumber ?? '?'} (базовая)`
                      : '—'}
                  />
                ) : (
                  <div style={{ padding: 32, textAlign: 'center', color: '#8c8c8c', border: '1px dashed #d9d9d9', borderRadius: 6 }}>
                    Выберите две версии конфигурации для сравнения
                  </div>
                )}
              </Space>
            ),
          },
          {
            key: 'templates',
            label: 'Шаблоны',
            children: (
              <Card title="Пример шаблона baseline">
                <Space direction="vertical">
                  <Paragraph>Шаблон можно применить к выбранным устройствам</Paragraph>
                  {[
                    { option: 'hostname.prefix', value: 'office-' },
                    { option: 'logging.buffered', value: '8192' },
                  ].map((item) => (
                    <div key={item.option} style={{ background: '#f8fafc', padding: '8px 12px', borderRadius: 6 }}>
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
  );
  };

  const handleAddDevice = async (device: Partial<Device>) => {
    try {
      const res = await devicesApi.add(device as Device);
      message.success('Устройство успешно добавлено');
      setDevices(prev => [...prev, res.data]);
    } catch (err) {
      message.error(getErrorMessage(err, 'Не удалось добавить устройство'));
    }
  };

  const selectedConfigVersion = useMemo(() => {
    if (!selectedConfigVersionId) return null;
    return configVersions.find((v) => v.id === selectedConfigVersionId) || null;
  }, [configVersions, selectedConfigVersionId]);

  // resolvedConfig — configData из истории (если есть) или парсим newConfig
  const resolvedConfig = (v: any): Record<string, any> | null => {
    if (!v) return null;
    if (v.configData && typeof v.configData === 'object') return v.configData;
    try { return JSON.parse(v.newConfig ?? '{}'); } catch { return null; }
  };

  // Левый (сравниваемый) и правый (базовый) конфиги для diff
  const diffLeftConfig = useMemo(() => {
    if (!compareVersionId) return null;
    const v = (configVersions as any[]).find((x: any) => x.id === compareVersionId);
    return resolvedConfig(v);
  }, [configVersions, compareVersionId]);

  const diffRightConfig = useMemo(() => {
    if (!selectedConfigVersionId) return null;
    const v = (configVersions as any[]).find((x: any) => x.id === selectedConfigVersionId);
    return resolvedConfig(v);
  }, [configVersions, selectedConfigVersionId]);

  const openConfigModalByType = (device?: Device) => {
    const target = device ?? selectedDevice ?? undefined;
    const typeCode = target?.typeCode;
    if (typeCode === 2) return setCiscoModalOpen(true);
    if (typeCode === 1) return setMfuModalOpen(true);
    // ПК (0) и VM (3) могут быть как Windows, так и Linux — выбираем окно по ОС
    if (typeCode === 0 || typeCode === 3) {
      return target?.operatingSystem === 'linux'
        ? setLinuxModalOpen(true)
        : setWindowsModalOpen(true);
    }
    message.info('Выберите устройство из списка');
  };

  const handleInventory = async (device: Device) => {
    if (!device.id) return;
    setInventoryLoadingId(device.id);
    message.loading({ content: `Инвентаризация ${device.hostname}...`, key: 'inventory', duration: 0 });
    try {
      const res = await devicesApi.inventory(device.id);
      if (res.success) {
        if (res.device) {
          setDevices((prev) => prev.map((d) => (d.id === device.id ? { ...d, ...res.device } : d)));
          setSelectedDevice((prev) => (prev?.id === device.id ? { ...prev, ...res.device } as Device : prev));
        }
        message.success({
          content: res.updated
            ? `Данные обновлены (${res.detectionMethod ?? 'опрос'})`
            : `Изменений нет (${res.detectionMethod ?? 'опрос'})`,
          key: 'inventory',
        });
      } else {
        message.warning({ content: res.message || 'Устройство не ответило на опрос', key: 'inventory' });
      }
    } catch (err: any) {
      message.error({
        content: `Ошибка инвентаризации: ${err.response?.data?.message || err.message}`,
        key: 'inventory',
      });
    } finally {
      setInventoryLoadingId(null);
    }
  };

  const handleScan = async (options: any) => {
    setShowScan(false);
    setIsScanning(true);
    setScanProgress(0);

    // Determine scan mode from current page type
    const scanMode = type || 'all';

    try {
      const startData = await devicesApi.startScan({ ...options, scanMode });
      const taskId = startData.taskId;
      message.info('Сканирование запущено...');

      let statusData: any = { status: 'running' };

      while (statusData.status === 'running') {
        await new Promise(r => setTimeout(r, 1500));
        statusData = await devicesApi.getScanStatus(taskId);
        // Реальный прогресс от числа просканированных адресов (backend отдаёт scanned/total/percent)
        if (typeof statusData.percent === 'number') {
          setScanProgress(statusData.percent);
          setScanStats({ scanned: statusData.scanned ?? 0, total: statusData.total ?? 0 });
        }
      }

      setScanProgress(100);

      if (statusData.status === 'completed' && Array.isArray(statusData.results)) {
        const allResults: Array<{ device: Device; scanStatus: string }> = statusData.results;

        const newDevices    = allResults.filter(r => r.scanStatus === 'NEW').map(r => r.device);
        const updatedDevices = allResults.filter(r => r.scanStatus === 'UPDATED').map(r => r.device);
        const existingCount = allResults.filter(r => r.scanStatus === 'EXISTING').length;

        setDevices(prev => {
          let list = [...prev];
          // Add brand-new devices (not yet in state)
          for (const d of newDevices) {
            if (!list.some(p => p.id === d.id)) list.push(d);
          }
          // Refresh updated devices in state
          for (const d of updatedDevices) {
            const idx = list.findIndex(p => p.id === d.id);
            if (idx >= 0) list[idx] = d;
            else list.push(d);
          }
          return list;
        });

        message.success(
          `Готово: ${allResults.length} устройств | ` +
          `Новых: ${newDevices.length} | ` +
          `Обновлено: ${updatedDevices.length} | ` +
          `Без изменений: ${existingCount}`
        );
      } else if (statusData.status === 'completed') {
        message.info('Сканирование завершено. Устройства не обнаружены.');
      }
    } catch (err: any) {
      message.error(`Ошибка сканирования: ${err.message}`);
    } finally {
      setIsScanning(false);
    }
  };

  const filteredDevices = useMemo(() => {
    const search = deviceSearch.trim().toLowerCase();
    if (!search) return devices;
    return devices.filter((d) =>
      [d.hostname, d.groupName, d.osVersion, d.type, d.manufacturer, d.model, ...(d.ips || [])]
        .some(val => val?.toLowerCase().includes(search))
    );
  }, [devices, deviceSearch]);

  return (
    <>
      <Tabs activeKey={activeTabKey} onChange={setActiveTabKey} items={[
        {
          key: 'devices',
          label: 'Список устройств',
          children: (
            <>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
                <h2>Устройства: {type ? deviceTypeInfo[routeTypeMap[type]]?.name : 'Всего'} ({filteredDevices.length} из {devices.length})</h2>
                <Space>
                  <Button icon={<ScanOutlined />} type="primary" onClick={() => setShowScan(true)}>Сканировать сеть</Button>
                  <Button icon={<PlusCircleOutlined />} onClick={() => setShowAddDevice(true)}>Добавить устройство</Button>
                </Space>
              </div>
              {isScanning && <ScanProgress progress={scanProgress} scanned={scanStats.scanned} total={scanStats.total} />}
              <Card style={{ marginBottom: 16 }}>
                <Search placeholder="Поиск по имени, IP, группе, ОС, производителю, модели или типу" value={deviceSearch} onChange={e => setDeviceSearch(e.target.value)} style={{ maxWidth: 620 }} />
              </Card>
              {loading ? (
                <div style={{ textAlign: 'center', padding: 40 }}>Загрузка...</div>
              ) : filteredDevices.length === 0 ? (
                <Empty description="Нет устройств" />
              ) : (
                <Card>
                  <Space wrap style={{ marginBottom: 16 }}>
                    <Text strong>Выбрано устройств: {selectedDeviceIds.length}</Text>
                    <Button 
                      danger 
                      icon={<DeleteOutlined />} 
                      onClick={handleBulkDelete}
                      disabled={selectedDeviceIds.length === 0 || deleting}
                      loading={deleting}
                    >
                      Удалить выбранные
                    </Button>
                    <Button disabled={selectedDeviceIds.length === 0} icon={<FileDoneOutlined />} type="primary" onClick={() => message.info('Массовое применение в разработке')}>Применить настройки</Button>
                    <Button disabled={selectedDeviceIds.length === 0} onClick={() => setSelectedDeviceIds([])}>Сбросить выбор</Button>
                  </Space>
                  <Table
                    rowKey="id"
                    rowSelection={{ selectedRowKeys: selectedDeviceIds, onChange: setSelectedDeviceIds }}
                    columns={deviceColumns}
                    dataSource={filteredDevices}
                    pagination={{ defaultPageSize: 15, showSizeChanger: true, showTotal: (total, range) => `${range[0]}-${range[1]} из ${total}` }}
                  />
                </Card>
              )}
            </>
          ),
        },
        {
          key: 'config',
          label: 'Просмотр и сравнение конфигурации',
          children: renderConfigTab(),
        },
        {
          key: 'terminal',
          label: 'Встроенный терминал',
          children: (
            <Card title="Административный терминал" extra={<Tag color={terminalConnected ? 'green' : 'orange'}>{terminalConnected ? 'Подключено' : 'Нет подключения'}</Tag>}>
              <Space direction="vertical" style={{ width: '100%' }}>
                <AutoComplete style={{ width: '100%' }} options={commandSuggestions.map(c => ({ value: c }))} value={terminalCommand} onChange={setTerminalCommand} placeholder="Введите команду" />
                <pre style={{ background: '#101820', color: '#e6f7ff', minHeight: 180, padding: 12 }}>$ {terminalCommand || 'show running-config'}{terminalConnected ? '\nВывод команды...' : '\nТерминал отключен'}</pre>
                <Space wrap>
                  <Button type="primary" icon={<CodeOutlined />} onClick={() => { setTerminalConnected(true); setShowSSH(true); }}>Открыть xterm.js</Button>
                  <Button icon={<FileSearchOutlined />}>Захватить конфигурацию</Button>
                  <Button danger disabled={!terminalConnected} onClick={() => { setTerminalConnected(false); setShowSSH(false); message.success('Отключено'); }}>Отключиться</Button>
                </Space>
                <Text type="secondary">Логи сессий сохраняются в аудите</Text>
              </Space>
            </Card>
          ),
        },
        {
          key: 'audit',
          label: 'Журнал аудита',
          children: renderAuditTab(),
        },
      ]} />

      <DeviceTerminal open={showSSH} onClose={() => setShowSSH(false)} device={{ id: selectedDevice?.id || 0, hostname: selectedDevice?.hostname || '', ip: selectedDevice?.ips?.[0] || '', os: selectedDevice?.operatingSystem || '' }} />
      <ScanDeviceModal open={showScan} onCancel={() => setShowScan(false)} onScan={handleScan} scanMode={type || 'all'} />
      <AddDeviceModal open={showAddDevice} onCancel={() => setShowAddDevice(false)} onAdd={handleAddDevice} />
      <ConfigLinuxModal open={linuxModalOpen} onClose={() => setLinuxModalOpen(false)} hostname={selectedDevice?.hostname} deviceId={selectedDevice?.id} />
      <ConfigWindowsModal open={windowsModalOpen} onClose={() => setWindowsModalOpen(false)} hostname={selectedDevice?.hostname} deviceId={selectedDevice?.id} />
      <ConfigMFUModal open={mfuModalOpen} onClose={() => setMfuModalOpen(false)} hostname={selectedDevice?.hostname} deviceId={selectedDevice?.id} />
      <ConfigCiscoModal open={ciscoModalOpen} onClose={() => setCiscoModalOpen(false)} hostname={selectedDevice?.hostname} deviceId={selectedDevice?.id} />
      <ResolveDriftModal
        open={!!driftDevice}
        device={driftDevice ? {
          id: driftDevice.id!,
          hostname: driftDevice.hostname,
          operatingSystem: driftDevice.operatingSystem,
          typeCode: driftDevice.typeCode,
        } : null}
        onClose={() => setDriftDevice(null)}
        onResolved={() => { loadDevices(); loadStatuses(); loadScheduled(); }}
      />
    </>
  );
}