import React, { useEffect, useState } from 'react';
import {
  Card, Form, Input, Switch, Button, message, Tabs, InputNumber, Modal,
  Select, Row, Col, Table, Tag, Space, Popconfirm, TimePicker, Tooltip, Alert,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, PlayCircleOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { devicesApi } from '../api/devicesApi';
import type { ScanSchedule, ScanCredential } from '../types';
import UserManager from '../components/UserManager';

const { TabPane } = Tabs;

const settingsCardStyle = { height: '100%', width: '100%' };
const settingsCardBodyStyle = { display: 'flex', flexDirection: 'column' as const, height: '100%' };

const SCAN_MODE_OPTIONS = [
  { value: 'all',     label: 'Все устройства' },
  { value: 'windows', label: 'ПК (Windows)' },
  { value: 'linux',   label: 'Linux / VM' },
  { value: 'cisco',   label: 'Cisco' },
  { value: 'mfu',     label: 'МФУ' },
];

const SCHEDULE_PRESETS = [
  { value: 'hourly',   label: 'Каждый час' },
  { value: 'every6h',  label: 'Каждые 6 часов' },
  { value: 'every12h', label: 'Каждые 12 часов' },
  { value: 'daily',    label: 'Каждый день в...' },
  { value: 'custom',   label: 'Своё расписание (cron)' },
];

function presetToCron(preset: string, time?: dayjs.Dayjs): string {
  switch (preset) {
    case 'hourly':   return '0 0 * * * *';
    case 'every6h':  return '0 0 */6 * * *';
    case 'every12h': return '0 0 */12 * * *';
    case 'daily':    return `0 ${time?.minute() ?? 0} ${time?.hour() ?? 2} * * *`;
    default:         return '';
  }
}

function cronToPreset(cron?: string): string {
  if (!cron) return 'daily';
  if (cron === '0 0 * * * *')   return 'hourly';
  if (cron === '0 0 */6 * * *')  return 'every6h';
  if (cron === '0 0 */12 * * *') return 'every12h';
  const parts = cron.split(' ');
  if (parts.length === 6 && parts[0] === '0' && parts[3] === '*' && parts[4] === '*' && parts[5] === '*') {
    return 'daily';
  }
  return 'custom';
}

function cronToTime(cron?: string): dayjs.Dayjs | null {
  if (!cron) return dayjs().hour(2).minute(0);
  const parts = cron.split(' ');
  if (parts.length >= 3) {
    const h = parseInt(parts[2], 10);
    const m = parseInt(parts[1], 10);
    if (!isNaN(h) && !isNaN(m)) return dayjs().hour(h).minute(m);
  }
  return dayjs().hour(2).minute(0);
}

function formatCronHuman(cron?: string): string {
  if (!cron) return '—';
  const preset = cronToPreset(cron);
  if (preset === 'hourly')   return 'Каждый час';
  if (preset === 'every6h')  return 'Каждые 6 часов';
  if (preset === 'every12h') return 'Каждые 12 часов';
  if (preset === 'daily') {
    const t = cronToTime(cron);
    return t ? `Каждый день в ${t.format('HH:mm')}` : 'Ежедневно';
  }
  return cron;
}

// ─── Schedule Tab ─────────────────────────────────────────────────────────────

function ScanScheduleTab() {
  const [schedules, setSchedules] = useState<ScanSchedule[]>([]);
  const [profiles, setProfiles] = useState<ScanCredential[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<ScanSchedule | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  const preset = Form.useWatch('schedulePreset', form);
  const snmpVersion = Form.useWatch('snmpVersion', form);
  const isV3 = snmpVersion === 'v3';

  const loadSchedules = async () => {
    setLoading(true);
    try {
      const data = await devicesApi.scanSchedules.getAll();
      setSchedules(data);
    } catch {
      message.error('Ошибка загрузки расписаний');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSchedules();
    devicesApi.scanCredentials.getAll().then(setProfiles).catch(() => setProfiles([]));
  }, []);

  const openCreate = () => {
    setEditTarget(null);
    form.setFieldsValue({
      name: '',
      subnet: '192.168.1.0',
      mask: 24,
      port: 161,
      community: 'public',
      snmpVersion: 'v2c',
      scanMode: 'all',
      schedulePreset: 'daily',
      dailyTime: dayjs().hour(2).minute(0),
      customCron: '',
      enabled: true,
      credentialId: undefined,
      snmpSecurityName: '',
      snmpAuthProtocol: 'SHA',
      snmpAuthPassword: '',
      snmpPrivProtocol: 'AES',
      snmpPrivPassword: '',
    });
    setModalOpen(true);
  };

  const openEdit = (record: ScanSchedule) => {
    setEditTarget(record);
    const p = cronToPreset(record.cronExpression);
    form.setFieldsValue({
      name: record.name,
      subnet: record.subnet,
      mask: record.mask,
      port: record.port,
      community: record.community,
      snmpVersion: record.snmpVersion,
      scanMode: record.scanMode,
      schedulePreset: p,
      dailyTime: cronToTime(record.cronExpression),
      customCron: p === 'custom' ? record.cronExpression : '',
      enabled: record.enabled,
      credentialId: record.credentialId,
      snmpSecurityName: record.snmpSecurityName ?? '',
      snmpAuthProtocol: record.snmpAuthProtocol ?? 'SHA',
      snmpPrivProtocol: record.snmpPrivProtocol ?? 'AES',
      // Пароли с сервера не возвращаются — оставляем пустыми (пусто = «не менять» при сохранении)
      snmpAuthPassword: '',
      snmpPrivPassword: '',
    });
    setModalOpen(true);
  };

  const handleSave = async () => {
    let values: any;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    setSaving(true);
    try {
      let cronExpression: string | undefined;
      if (values.schedulePreset === 'custom') {
        cronExpression = values.customCron || undefined;
      } else if (values.schedulePreset === 'daily') {
        cronExpression = presetToCron('daily', values.dailyTime);
      } else {
        cronExpression = presetToCron(values.schedulePreset);
      }

      const payload: Omit<ScanSchedule, 'id' | 'lastRunAt' | 'lastRunStatus' | 'createdAt'> = {
        name:          values.name,
        subnet:        values.subnet,
        mask:          values.mask,
        port:          values.port,
        community:     values.community,
        snmpVersion:   values.snmpVersion,
        scanMode:      values.scanMode,
        cronExpression,
        enabled:       values.enabled,
        credentialId:  values.credentialId ?? undefined,
      };

      // SNMPv3 — логин и пароли аутентификации/шифрования
      if (values.snmpVersion === 'v3') {
        payload.snmpSecurityName = values.snmpSecurityName || undefined;
        payload.snmpAuthProtocol = values.snmpAuthProtocol || undefined;
        payload.snmpAuthPassword = values.snmpAuthPassword || undefined;
        payload.snmpPrivProtocol = values.snmpPrivProtocol || undefined;
        payload.snmpPrivPassword = values.snmpPrivPassword || undefined;
      }

      if (editTarget?.id) {
        const updated = await devicesApi.scanSchedules.update(editTarget.id, payload);
        setSchedules(prev => prev.map(s => s.id === editTarget.id ? updated : s));
        message.success('Расписание обновлено');
      } else {
        const created = await devicesApi.scanSchedules.create(payload);
        setSchedules(prev => [...prev, created]);
        message.success('Расписание создано');
      }
      setModalOpen(false);
    } catch {
      message.error('Ошибка сохранения расписания');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await devicesApi.scanSchedules.delete(id);
      setSchedules(prev => prev.filter(s => s.id !== id));
      message.success('Расписание удалено');
    } catch {
      message.error('Ошибка удаления');
    }
  };

  const handleRunNow = async (id: number, name: string) => {
    try {
      await devicesApi.scanSchedules.runNow(id);
      message.info(`Сканирование "${name}" запущено`);
    } catch {
      message.error('Ошибка запуска сканирования');
    }
  };

  const statusTag = (status?: string) => {
    if (!status) return <Tag color="default">—</Tag>;
    if (status === 'success') return <Tag color="success">Успешно</Tag>;
    if (status === 'failed')  return <Tag color="error">Ошибка</Tag>;
    return <Tag color="processing">{status}</Tag>;
  };

  const scanModeLabel = (mode: string) =>
    SCAN_MODE_OPTIONS.find(o => o.value === mode)?.label ?? mode;

  const columns = [
    { title: 'Название', dataIndex: 'name', key: 'name' },
    {
      title: 'Подсеть',
      key: 'subnet',
      render: (_: any, r: ScanSchedule) => `${r.subnet}/${r.mask}`,
    },
    {
      title: 'Режим',
      dataIndex: 'scanMode',
      key: 'scanMode',
      render: (v: string) => <Tag>{scanModeLabel(v)}</Tag>,
    },
    {
      title: 'SNMP',
      key: 'snmp',
      render: (_: any, r: ScanSchedule) =>
        r.snmpVersion === 'v3'
          ? `v3 / ${r.snmpSecurityName || '—'}`
          : `${r.snmpVersion} / ${r.community}`,
    },
    {
      title: 'Профиль доступа',
      key: 'credential',
      render: (_: any, r: ScanSchedule) => {
        const p = r.credentialId != null ? profiles.find((x) => x.id === r.credentialId) : undefined;
        return p ? <Tag color="geekblue">{p.name}</Tag> : <span style={{ color: '#aaa' }}>—</span>;
      },
    },
    {
      title: 'Расписание',
      key: 'cron',
      render: (_: any, r: ScanSchedule) => (
        <Tooltip title={r.cronExpression}>{formatCronHuman(r.cronExpression)}</Tooltip>
      ),
    },
    {
      title: 'Активно',
      dataIndex: 'enabled',
      key: 'enabled',
      render: (v: boolean) => <Tag color={v ? 'success' : 'default'}>{v ? 'Да' : 'Нет'}</Tag>,
    },
    {
      title: 'Последний запуск',
      key: 'lastRun',
      render: (_: any, r: ScanSchedule) => (
        <Space direction="vertical" size={2}>
          <span>{r.lastRunAt ? new Date(r.lastRunAt).toLocaleString('ru-RU') : '—'}</span>
          {statusTag(r.lastRunStatus)}
        </Space>
      ),
    },
    {
      title: 'Действия',
      key: 'actions',
      render: (_: any, r: ScanSchedule) => (
        <Space>
          <Tooltip title="Запустить сейчас">
            <Button
              icon={<PlayCircleOutlined />}
              onClick={() => handleRunNow(r.id!, r.name)}
              size="small"
            />
          </Tooltip>
          <Tooltip title="Редактировать">
            <Button icon={<EditOutlined />} onClick={() => openEdit(r)} size="small" />
          </Tooltip>
          <Popconfirm
            title="Удалить расписание?"
            okText="Да"
            cancelText="Нет"
            onConfirm={() => handleDelete(r.id!)}
          >
            <Button icon={<DeleteOutlined />} danger size="small" />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="Расписание автосканирования сети"
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          Добавить расписание
        </Button>
      }
    >
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={schedules}
        pagination={{ pageSize: 10 }}
      />

      <Modal
        title={editTarget ? 'Редактировать расписание' : 'Новое расписание сканирования'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSave}
        confirmLoading={saving}
        okText="Сохранить"
        cancelText="Отмена"
        width={560}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 8 }}>
          <Form.Item name="name" label="Название" rules={[{ required: true, message: 'Введите название' }]}>
            <Input placeholder="Например: Сеть офис 192.168.1.0/24" />
          </Form.Item>

          <Row gutter={12}>
            <Col span={16}>
              <Form.Item name="subnet" label="Подсеть (начальный IP)" rules={[{ required: true }]}>
                <Input placeholder="192.168.1.0" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="mask" label="Маска /CIDR" rules={[{ required: true }]}>
                <InputNumber min={16} max={24} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="community" label="Community string" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="snmpVersion" label="Версия SNMP" rules={[{ required: true }]}>
                <Select options={[
                  { value: 'v1',  label: 'SNMP v1' },
                  { value: 'v2c', label: 'SNMP v2c' },
                  { value: 'v3',  label: 'SNMP v3' },
                ]} />
              </Form.Item>
            </Col>
          </Row>

          {isV3 && (
            <>
              <Alert
                type="warning"
                showIcon
                style={{ marginBottom: 12 }}
                message="SNMP v3 использует логин и пароли вместо community"
                description="Уровень безопасности определяется автоматически: без паролей — noAuthNoPriv, только пароль аутентификации — authNoPriv, оба пароля — authPriv. При редактировании пустой пароль означает «не менять»."
              />
              <Form.Item name="snmpSecurityName" label="Логин (Security Name)">
                <Input autoComplete="off" placeholder="например, snmpadmin" />
              </Form.Item>
              <Row gutter={12}>
                <Col span={12}>
                  <Form.Item name="snmpAuthProtocol" label="Протокол аутентификации">
                    <Select options={[
                      { value: 'SHA',    label: 'SHA-1' },
                      { value: 'SHA256', label: 'SHA-256' },
                      { value: 'MD5',    label: 'MD5' },
                    ]} />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item name="snmpAuthPassword" label="Пароль аутентификации">
                    <Input.Password autoComplete="new-password" placeholder="пусто — noAuthNoPriv" />
                  </Form.Item>
                </Col>
              </Row>
              <Row gutter={12}>
                <Col span={12}>
                  <Form.Item name="snmpPrivProtocol" label="Протокол шифрования">
                    <Select options={[
                      { value: 'AES',    label: 'AES-128' },
                      { value: 'AES256', label: 'AES-256' },
                      { value: 'DES',    label: 'DES' },
                    ]} />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item name="snmpPrivPassword" label="Пароль шифрования">
                    <Input.Password autoComplete="new-password" placeholder="пусто — authNoPriv" />
                  </Form.Item>
                </Col>
              </Row>
            </>
          )}

          <Form.Item name="scanMode" label="Режим сканирования" rules={[{ required: true }]}>
            <Select options={SCAN_MODE_OPTIONS} />
          </Form.Item>

          <Form.Item
            name="credentialId"
            label="Профиль доступа (SSH/WinRM)"
            tooltip="Креды для опроса Windows/Linux. Пароли хранятся зашифрованно. Управление профилями — при ручном сканировании."
          >
            <Select
              allowClear
              placeholder="Без профиля (только SNMP)"
              options={profiles.map((p) => ({
                value: p.id,
                label: `${p.name}${p.domain ? ` · ${p.domain}` : ''}`,
              }))}
              notFoundContent="Профилей пока нет"
            />
          </Form.Item>

          <Form.Item name="schedulePreset" label="Периодичность" rules={[{ required: true }]}>
            <Select options={SCHEDULE_PRESETS} />
          </Form.Item>

          {preset === 'daily' && (
            <Form.Item name="dailyTime" label="Время запуска" rules={[{ required: true }]}>
              <TimePicker format="HH:mm" minuteStep={15} style={{ width: '100%' }} />
            </Form.Item>
          )}

          {preset === 'custom' && (
            <Form.Item
              name="customCron"
              label="Cron-выражение (6 полей: сек мин час д.м. мес д.н.)"
              tooltip='Пример: "0 30 3 * * *" — каждый день в 3:30'
              rules={[{ required: true, message: 'Введите cron-выражение' }]}
            >
              <Input placeholder="0 0 2 * * *" />
            </Form.Item>
          )}

          <Form.Item name="enabled" label="Активно" valuePropName="checked">
            <Switch checkedChildren="Вкл" unCheckedChildren="Выкл" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}

// ─── Main AdminPage ────────────────────────────────────────────────────────────

export default function AdminPage() {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [changePasswordModalOpen, setChangePasswordModalOpen] = useState(false);
  const adEnabled = Form.useWatch('adEnabled', form);

  useEffect(() => {
    form.setFieldsValue({
      backupEnabled: true,
      configCollectionIntervalHours: 24,
      backupTaskStartTime: '02:00',
      configVersionRetentionDepth: 30,
      obsoleteVersionPolicy: 'keep-last-successful',
      loggingLevel: 'INFO',
      auditRetentionDays: 180,
      logSuccessfulLogins: true,
      logFailedLogins: true,
      sessionLifetimeMinutes: 60,
      maxFailedLoginAttempts: 5,
      workerThreads: 4,
      redisHost: 'localhost',
      redisPort: 6379,
      retryIntervalSeconds: 60,
      maxJobExecutionMinutes: 30,
      sshTimeoutSeconds: 30,
      winrmTimeoutSeconds: 45,
      reconnectAttempts: 3,
      snmpCommunity: 'public',
      snmpVersion: '2c',
      snmpTimeoutSeconds: 10,
      adEnabled: false,
      adUrl: 'ldap://dc.company.local:389',
      adBaseDn: 'DC=company,DC=local',
      adUserSearchFilter: '(sAMAccountName={0})',
    });
  }, [form]);

  const handleSubmit = async (values: any) => {
    try {
      setLoading(true);
      await fetch('/api/settings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(values),
      });
      message.success('Настройки сохранены');
    } catch {
      message.error('Ошибка при сохранении');
    } finally {
      setLoading(false);
    }
  };

  const handleChangePassword = async (values: any) => {
    try {
      await fetch('/api/auth/change-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(values),
      });
      message.success('Пароль изменён');
      setChangePasswordModalOpen(false);
    } catch {
      message.error('Ошибка изменения пароля');
    }
  };

  return (
    <>
      <Tabs defaultActiveKey="1">
        <TabPane tab="Общие настройки" key="1">
          <Form form={form} layout="vertical" onFinish={handleSubmit}>
            <Row gutter={[16, 16]} align="stretch">
              <Col xs={24} lg={12} style={{ display: 'flex' }}>
                <Card title="Настройки резервного копирования конфигураций" style={settingsCardStyle} styles={{ body: settingsCardBodyStyle }}>
                  <Form.Item name="backupEnabled" label="Автоматическое резервное копирование" valuePropName="checked">
                    <Switch checkedChildren="Вкл" unCheckedChildren="Выкл" />
                  </Form.Item>
                  <Form.Item name="configCollectionIntervalHours" label="Периодичность автоматического сбора конфигураций (часов)" rules={[{ required: true, type: 'number', min: 1 }]}>
                    <InputNumber min={1} style={{ width: '100%' }} />
                  </Form.Item>
                  <Form.Item name="backupTaskStartTime" label="Время запуска задач" rules={[{ required: true }]}>
                    <Input placeholder="02:00" />
                  </Form.Item>
                  <Form.Item name="configVersionRetentionDepth" label="Глубина хранения версий" rules={[{ required: true, type: 'number', min: 1 }]}>
                    <InputNumber min={1} style={{ width: '100%' }} />
                  </Form.Item>
                  <Form.Item name="obsoleteVersionPolicy" label="Политика удаления устаревших версий" rules={[{ required: true }]}>
                    <Select options={[
                      { value: 'keep-last-successful',  label: 'Хранить последние успешные версии' },
                      { value: 'delete-after-retention', label: 'Удалять после истечения срока хранения' },
                      { value: 'archive-before-delete',  label: 'Архивировать перед удалением' },
                    ]} />
                  </Form.Item>
                </Card>
              </Col>

              <Col xs={24} lg={12} style={{ display: 'flex' }}>
                <Card title="Настройки аудита и журналирования" style={settingsCardStyle} styles={{ body: settingsCardBodyStyle }}>
                  <Form.Item name="loggingLevel" label="Уровень логирования" rules={[{ required: true }]}>
                    <Select options={[{ value: 'DEBUG' }, { value: 'INFO' }, { value: 'WARN' }, { value: 'ERROR' }]} />
                  </Form.Item>
                  <Form.Item name="auditRetentionDays" label="Срок хранения аудита (дней)" rules={[{ required: true, type: 'number', min: 1 }]}>
                    <InputNumber min={1} style={{ width: '100%' }} />
                  </Form.Item>
                  <Form.Item name="logSuccessfulLogins" label="Запись успешных попыток входа" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                  <Form.Item name="logFailedLogins" label="Запись неуспешных попыток входа" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Card>
              </Col>

              <Col xs={24} lg={12} style={{ display: 'flex' }}>
                <Card title="Настройки безопасности" style={settingsCardStyle} styles={{ body: settingsCardBodyStyle }}>
                  <Form.Item name="sessionLifetimeMinutes" label="Время жизни пользовательской сессии (минут)" rules={[{ required: true, type: 'number', min: 5 }]}>
                    <InputNumber min={5} style={{ width: '100%' }} />
                  </Form.Item>
                  <Form.Item name="maxFailedLoginAttempts" label="Количество неудачных попыток входа" rules={[{ required: true, type: 'number', min: 1 }]}>
                    <InputNumber min={1} style={{ width: '100%' }} />
                  </Form.Item>
                </Card>
              </Col>

              <Col xs={24} lg={12} style={{ display: 'flex' }}>
                <Card title="Настройки очередей и фоновых задач" style={settingsCardStyle} styles={{ body: settingsCardBodyStyle }}>
                  <Form.Item name="workerThreads" label="Количество потоков обработки" rules={[{ required: true, type: 'number', min: 1 }]}>
                    <InputNumber min={1} style={{ width: '100%' }} />
                  </Form.Item>
                  <Form.Item name="redisHost" label="Redis host" rules={[{ required: true }]}>
                    <Input placeholder="localhost" />
                  </Form.Item>
                  <Form.Item name="redisPort" label="Redis port" rules={[{ required: true, type: 'number', min: 1, max: 65535 }]}>
                    <InputNumber min={1} max={65535} style={{ width: '100%' }} />
                  </Form.Item>
                  <Form.Item name="retryIntervalSeconds" label="Интервалы повторных попыток (секунд)" rules={[{ required: true, type: 'number', min: 1 }]}>
                    <InputNumber min={1} style={{ width: '100%' }} />
                  </Form.Item>
                  <Form.Item name="maxJobExecutionMinutes" label="Максимальное время выполнения задания (минут)" rules={[{ required: true, type: 'number', min: 1 }]}>
                    <InputNumber min={1} style={{ width: '100%' }} />
                  </Form.Item>
                </Card>
              </Col>

              <Col xs={24} style={{ display: 'flex' }}>
                <Card title="Настройки устройств по умолчанию" style={settingsCardStyle} styles={{ body: settingsCardBodyStyle }}>
                  <Row gutter={16} style={{ width: '100%' }}>
                    <Col xs={24} md={8}>
                      <Form.Item name="sshTimeoutSeconds" label="Таймаут подключения SSH (секунд)" rules={[{ required: true, type: 'number', min: 1 }]}>
                        <InputNumber min={1} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="winrmTimeoutSeconds" label="Таймаут WinRM (секунд)" rules={[{ required: true, type: 'number', min: 1 }]}>
                        <InputNumber min={1} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="reconnectAttempts" label="Количество повторных подключений" rules={[{ required: true, type: 'number', min: 0 }]}>
                        <InputNumber min={0} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="snmpCommunity" label="SNMP community" rules={[{ required: true }]}>
                        <Input placeholder="public" />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="snmpVersion" label="SNMP version" rules={[{ required: true }]}>
                        <Select options={[{ value: '1', label: 'v1' }, { value: '2c', label: 'v2c' }, { value: '3', label: 'v3' }]} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="snmpTimeoutSeconds" label="SNMP timeout (секунд)" rules={[{ required: true, type: 'number', min: 1 }]}>
                        <InputNumber min={1} style={{ width: '100%' }} />
                      </Form.Item>
                    </Col>
                  </Row>
                </Card>
              </Col>
            </Row>

            <Button type="primary" htmlType="submit" loading={loading} style={{ marginTop: 16 }}>
              Сохранить общие настройки
            </Button>
          </Form>
        </TabPane>

        <TabPane tab="Пользователи" key="users">
          <UserManager />
        </TabPane>

        <TabPane tab="Расписание сканирования" key="scan-schedule">
          <ScanScheduleTab />
        </TabPane>

        <TabPane tab="Active Directory" key="2">
          <Card title="Настройка интеграции с Active Directory">
            <Form form={form} layout="vertical" onFinish={handleSubmit}>
              <Form.Item name="adEnabled" label="Включить AD" valuePropName="checked">
                <Switch />
              </Form.Item>
              <Form.Item name="adUrl" label="URL LDAP (AD)" rules={[{ required: true }]}>
                <Input placeholder="ldap://dc.company.local:389" disabled={!adEnabled} />
              </Form.Item>
              <Form.Item name="adBaseDn" label="Базовый DN">
                <Input placeholder="DC=company,DC=local" disabled={!adEnabled} />
              </Form.Item>
              <Form.Item name="adUserSearchFilter" label="Фильтр поиска пользователя">
                <Input placeholder="(sAMAccountName={0})" disabled={!adEnabled} />
              </Form.Item>
              <Button type="primary" htmlType="submit" loading={loading}>
                Сохранить настройки AD
              </Button>
            </Form>
          </Card>
        </TabPane>

        <TabPane tab="Смена пароля admin" key="3">
          <Card title="Изменение пароля пользователя admin">
            <Button type="primary" onClick={() => setChangePasswordModalOpen(true)}>
              Изменить пароль
            </Button>
          </Card>
        </TabPane>

        <TabPane tab="Отчёты" key="4">
          <Card>Здесь будут отчёты об изменениях и статистике.</Card>
        </TabPane>
      </Tabs>

      <Modal
        title="Изменить пароль администратора"
        open={changePasswordModalOpen}
        onCancel={() => setChangePasswordModalOpen(false)}
        footer={null}
      >
        <Form onFinish={handleChangePassword} layout="vertical">
          <Form.Item name="currentPassword" label="Текущий пароль" rules={[{ required: true }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item name="newPassword" label="Новый пароль" rules={[{ required: true }, { min: 8, message: 'Минимум 8 символов' }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item
            name="confirmNewPassword"
            label="Повторите пароль"
            dependencies={['newPassword']}
            rules={[
              { required: true },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) return Promise.resolve();
                  return Promise.reject(new Error('Пароли не совпадают'));
                },
              }),
            ]}
          >
            <Input.Password />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>Сохранить</Button>
        </Form>
      </Modal>
    </>
  );
}
