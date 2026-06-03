import React, { useEffect, useMemo, useState } from 'react';
import { Card, DatePicker, Select, Space, Table, Tag, Typography } from 'antd';
import dayjs from 'dayjs';
import { eventApi } from '../api/eventApi';
import { authApi } from '../api/authApi';
import type { AuditLog } from '../types';

const { Title, Text } = Typography;

type AuditRow = {
  key: number | string;
  timestamp: number;
  datetime: string;
  user: string;
  action: string;
  device: string;
  deviceType: string;
  result: string;
};

const demoAuditRows: AuditRow[] = [
  { key: 'login-admin', timestamp: Date.now() - 10 * 60_000, datetime: new Date(Date.now() - 10 * 60_000).toLocaleString('ru-RU'), user: 'admin', action: 'Вход в приложение', device: '—', deviceType: 'Приложение', result: 'Успех' },
  { key: 'login-operator', timestamp: Date.now() - 35 * 60_000, datetime: new Date(Date.now() - 35 * 60_000).toLocaleString('ru-RU'), user: 'operator', action: 'Вход в приложение', device: '—', deviceType: 'Приложение', result: 'Успех' },
  { key: 'cfg-cisco', timestamp: Date.now() - 80 * 60_000, datetime: new Date(Date.now() - 80 * 60_000).toLocaleString('ru-RU'), user: 'network-engineer', action: 'Применение конфигурации', device: 'cisco-03', deviceType: 'Cisco', result: 'Успех' },
  { key: 'scan-pc', timestamp: Date.now() - 140 * 60_000, datetime: new Date(Date.now() - 140 * 60_000).toLocaleString('ru-RU'), user: 'operator', action: 'Инвентаризация', device: 'пк-01', deviceType: 'ПК', result: 'Успех' },
  { key: 'rollback-vm', timestamp: Date.now() - 220 * 60_000, datetime: new Date(Date.now() - 220 * 60_000).toLocaleString('ru-RU'), user: 'admin', action: 'Откат конфигурации', device: 'vm-04', deviceType: 'VM / Proxmox', result: 'Ошибка' },
];

const mapLogToRow = (log: AuditLog, index: number): AuditRow => {
  const timestamp = log.createdAt ? new Date(log.createdAt).getTime() : Date.now() - index * 60_000;

  return {
    key: log.id || `api-${index}`,
    timestamp,
    datetime: new Date(timestamp).toLocaleString('ru-RU'),
    user: log.userId || 'system',
    action: log.actionType,
    device: log.targetDeviceId ? `ID ${log.targetDeviceId}` : '—',
    deviceType: log.targetDeviceId ? 'Устройство' : 'Приложение',
    result: log.status === 'SUCCESS' ? 'Успех' : log.status === 'FAILED' ? 'Ошибка' : log.status,
  };
};

export default function AuditPage() {
  const [rows, setRows] = useState<AuditRow[]>(demoAuditRows);
  const [dateRange, setDateRange] = useState<[any, any] | null>(null);
  const [user, setUser] = useState<string>();
  const [action, setAction] = useState<string>();
  const [device, setDevice] = useState<string>();
  const [deviceType, setDeviceType] = useState<string>();
  const [result, setResult] = useState<string>();
  const [currentUser, setCurrentUser] = useState<string>('');

  // Получаем текущего пользователя
  useEffect(() => {
    const loadCurrentUser = async () => {
      try {
        const token = authApi.getToken();
        if (token) {
          const userData = await authApi.getCurrentUser(token);
          setCurrentUser(typeof userData === 'string' ? userData : userData.username);
        }
      } catch (error) {
        console.error('Failed to load current user:', error);
      }
    };

    loadCurrentUser();
  }, []);

  // Загрузка аудит логов
  useEffect(() => {
    const loadAudit = async () => {
      try {
        const res = await eventApi.getLogs({ size: 100 });
        const apiRows = res.data.map(mapLogToRow);
        const merged = [
          ...apiRows,
          ...demoAuditRows.filter((demoRow) => !apiRows.some((row) => row.key === demoRow.key)),
        ];
        setRows(merged.sort((a, b) => b.timestamp - a.timestamp));
      } catch (err) {
        console.error('Failed to load audit logs:', err);
        setRows(demoAuditRows);
      }
    };

    loadAudit();
  }, []);

  const filterOptions = (field: keyof Pick<AuditRow, 'datetime' | 'user' | 'action' | 'device' | 'deviceType' | 'result'>) => (
    Array.from(new Set(rows.map((row) => row[field]))).map((value) => ({ text: value, value }))
  );

  const selectOptions = (field: keyof Pick<AuditRow, 'user' | 'action' | 'device' | 'deviceType' | 'result'>) => (
    filterOptions(field).map(({ value }) => ({ value, label: value }))
  );

  const filteredRows = useMemo(() => rows.filter((row) => {
    const rowDate = dayjs(row.timestamp);
    const matchesDate = !dateRange || !dateRange[0] || !dateRange[1] || (
      rowDate.isAfter(dateRange[0].startOf('day')) && rowDate.isBefore(dateRange[1].endOf('day'))
    );

    return matchesDate
      && (!user || row.user === user)
      && (!action || row.action === action)
      && (!device || row.device === device)
      && (!deviceType || row.deviceType === deviceType)
      && (!result || row.result === result);
  }), [rows, dateRange, user, action, device, deviceType, result]);

  const columns = [
    { title: 'Дата/время', dataIndex: 'datetime', filters: filterOptions('datetime'), onFilter: (value: React.Key | boolean, record: AuditRow) => record.datetime === value },
    { title: 'Ответственный', dataIndex: 'user', filters: filterOptions('user'), onFilter: (value: React.Key | boolean, record: AuditRow) => record.user === value },
    { title: 'Действие', dataIndex: 'action', filters: filterOptions('action'), onFilter: (value: React.Key | boolean, record: AuditRow) => record.action === value },
    { title: 'Устройство/объект', dataIndex: 'device', filters: filterOptions('device'), onFilter: (value: React.Key | boolean, record: AuditRow) => record.device === value },
    { title: 'Тип', dataIndex: 'deviceType', filters: filterOptions('deviceType'), onFilter: (value: React.Key | boolean, record: AuditRow) => record.deviceType === value },
    {
      title: 'Результат',
      dataIndex: 'result',
      filters: filterOptions('result'),
      onFilter: (value: React.Key | boolean, record: AuditRow) => record.result === value,
      render: (value: string) => <Tag color={value === 'Успех' ? 'success' : value === 'Ошибка' ? 'error' : 'processing'}>{value}</Tag>,
    },
  ];

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      <div>
        <Title level={2}>Журнал аудита</Title>
        <Text type="secondary">
          Все события со всех устройств, включая события входа в приложение.
          {currentUser && <span> Текущий пользователь: <Tag color="blue">{currentUser}</Tag></span>}
        </Text>
      </div>

      <Card title="Фильтры аудита">
        <Space wrap>
          <DatePicker.RangePicker onChange={(value) => setDateRange(value as [any, any] | null)} />
          <Select allowClear placeholder="Ответственный" style={{ width: 190 }} onChange={setUser} options={selectOptions('user')} />
          <Select allowClear placeholder="Действие" style={{ width: 220 }} onChange={setAction} options={selectOptions('action')} />
          <Select allowClear placeholder="Устройство/объект" style={{ width: 220 }} onChange={setDevice} options={selectOptions('device')} />
          <Select allowClear placeholder="Тип" style={{ width: 190 }} onChange={setDeviceType} options={selectOptions('deviceType')} />
          <Select allowClear placeholder="Результат" style={{ width: 160 }} onChange={setResult} options={selectOptions('result')} />
        </Space>
      </Card>

      <Card title="События аудита" extra={<Tag color="blue">{filteredRows.length} событий</Tag>}>
        <Table rowKey="key" columns={columns} dataSource={filteredRows} pagination={{ pageSize: 15 }} />
      </Card>
    </Space>
  );
}