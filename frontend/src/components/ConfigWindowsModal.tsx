import React, { useState } from 'react';
import {
  Modal, Form, Input, InputNumber, Select, Switch, Button, Tabs,
  Divider, Space, Typography, Alert, message,
} from 'antd';
import { PlusOutlined, DeleteOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { configApi } from '../api/configApi';
import SaveToMemoryToggle from './SaveToMemoryToggle';

const { Text } = Typography;

interface FwRule   { _key: string; name: string; port: number; proto: string; direction: string }
interface SvcItem  { _key: string; name: string; startType: string; state: string }
interface LocalUser { _key: string; name: string; password: string; admin: boolean; disabled: boolean }
interface AuditCat { _key: string; name: string }

function uid() { return Math.random().toString(36).slice(2, 8); }

interface Props { open: boolean; onClose: () => void; hostname?: string; deviceId?: number }

const TIMEZONES_WIN = [
  { value: 'Russian Standard Time',          label: 'Москва (UTC+3)' },
  { value: 'UTC',                             label: 'UTC' },
  { value: 'Eastern Standard Time',          label: 'New York (UTC-5)' },
  { value: 'Central European Standard Time', label: 'Берлин (UTC+1)' },
  { value: 'China Standard Time',            label: 'Пекин (UTC+8)' },
];

export default function ConfigWindowsModal({ open, onClose, hostname, deviceId }: Props) {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [saveToMemory, setSaveToMemory] = useState(true);

  const [fwRules,    setFwRules]    = useState<FwRule[]>([]);
  const [services,   setServices]   = useState<SvcItem[]>([]);
  const [localUsers, setLocalUsers] = useState<LocalUser[]>([]);
  const [auditCats,  setAuditCats]  = useState<AuditCat[]>([]);

  const changeComputerName = Form.useWatch('changeComputerName', form);
  const autoLogonEnabled   = Form.useWatch('autoLogonEnabled',   form);
  const joinDomain         = Form.useWatch('joinDomain',         form);
  const setStaticIp        = Form.useWatch('setStaticIp',        form);

  const handleSubmit = async () => {
    if (!deviceId) return message.error('Устройство не выбрано');
    let fields: Record<string, any>;
    try { fields = await form.validateFields(); } catch { return; }

    const { winrmUsername, winrmPassword, winrmPort, ...configFields } = fields as Record<string, any>;
    const configData = {
      ...configFields,
      firewallOpenPorts: fwRules.map(({ _key, ...r }) => r),
      services:          services.map(({ _key, ...r }) => r),
      localUsers:        localUsers.map(({ _key, ...r }) => ({ ...r, create: true }) ),
      auditCategories:   auditCats.map(a => a.name).filter(Boolean),
      createRestorePoint: saveToMemory,
    };

    setSubmitting(true);
    try {
      await configApi.applyConfig({
        deviceIds: [deviceId],
        configData,
        credentials: { [deviceId]: { username: winrmUsername, password: winrmPassword, port: winrmPort ?? 5985 } },
      });
      message.success('Windows конфигурация применена' + (saveToMemory ? ' (точка восстановления создана)' : ''));
      onClose();
    } catch (e: any) {
      message.error(e?.response?.data?.message || 'Ошибка');
    } finally {
      setSubmitting(false);
    }
  };

  const tabItems = [
    {
      key: 'system', label: 'Система',
      children: (
        <>
          <Form.Item label="Переименовать компьютер" name="changeComputerName" valuePropName="checked" initialValue={false}>
            <Switch />
          </Form.Item>
          {changeComputerName && (
            <Form.Item label="Новое имя" name="newComputerName"
              rules={[{ required: true }, { pattern: /^[a-zA-Z0-9-]{1,15}$/, message: 'До 15 символов: латиница, цифры, дефис' }]}>
              <Input placeholder="WS-IVANOV" style={{ maxWidth: 260 }} />
            </Form.Item>
          )}

          <Space wrap>
            <Form.Item label="Часовой пояс" name="timezone" initialValue="Russian Standard Time" style={{ minWidth: 240 }}>
              <Select options={TIMEZONES_WIN} />
            </Form.Item>
            <Form.Item label="NTP-сервер" name="ntpServer" initialValue="time.windows.com" style={{ minWidth: 220 }}>
              <Input />
            </Form.Item>
          </Space>

          <Form.Item label="Вступить в домен Active Directory" name="joinDomain" valuePropName="checked" initialValue={false}>
            <Switch />
          </Form.Item>
          {joinDomain && (
            <Space wrap>
              <Form.Item label="Домен" name="domainName" rules={[{ required: true }]} style={{ minWidth: 200 }}>
                <Input placeholder="corp.local" />
              </Form.Item>
              <Form.Item label="Пользователь AD" name="domainUser" rules={[{ required: true }]} style={{ minWidth: 200 }}>
                <Input placeholder="Administrator" />
              </Form.Item>
              <Form.Item label="Пароль AD" name="domainPassword" rules={[{ required: true }]} style={{ minWidth: 200 }}>
                <Input.Password />
              </Form.Item>
            </Space>
          )}

          <Divider orientationMargin={0}>Прочее</Divider>
          <Space wrap>
            <Form.Item label="Политика PowerShell" name="executionPolicy" initialValue="RemoteSigned" style={{ width: 200 }}>
              <Select options={['Restricted','AllSigned','RemoteSigned','Unrestricted'].map(v => ({ value: v, label: v }))} />
            </Form.Item>
            <Form.Item label="Блокировка (мин)" name="inactivityTimeout" initialValue={15} style={{ width: 180 }}>
              <InputNumber min={0} max={480} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.Item label="Включить PSRemoting / WinRM" name="configureWinrm" valuePropName="checked" initialValue={false}>
            <Switch />
          </Form.Item>
        </>
      ),
    },
    {
      key: 'network', label: 'Сеть',
      children: (
        <>
          <Form.Item label="Задать статический IP" name="setStaticIp" valuePropName="checked" initialValue={false}>
            <Switch />
          </Form.Item>
          {setStaticIp && (
            <Space wrap>
              <Form.Item label="Адаптер" name="networkAdapter" style={{ minWidth: 160 }}>
                <Input placeholder="Ethernet" />
              </Form.Item>
              <Form.Item label="IP-адрес" name="ipAddress" style={{ minWidth: 160 }}>
                <Input placeholder="192.168.1.100" />
              </Form.Item>
              <Form.Item label="Prefix-длина" name="prefixLength" initialValue="24" style={{ width: 140 }}>
                <InputNumber min={1} max={32} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item label="Шлюз" name="gateway" style={{ minWidth: 160 }}>
                <Input placeholder="192.168.1.1" />
              </Form.Item>
            </Space>
          )}

          <Form.Item label="DNS-серверы" name="dnsServers" tooltip="Через пробел">
            <Input placeholder="8.8.8.8 8.8.4.4" style={{ maxWidth: 300 }} />
          </Form.Item>

          <Divider orientationMargin={0}>SMB / WSUS</Divider>
          <Form.Item label="SMBv2" name="smbEnabled" valuePropName="checked" initialValue={true}>
            <Switch checkedChildren="включён" unCheckedChildren="SMBv1 отключён" />
          </Form.Item>
          <Form.Item label="WSUS-сервер" name="wsusServer">
            <Input placeholder="http://wsus.corp.local:8530" style={{ maxWidth: 340 }} />
          </Form.Item>
        </>
      ),
    },
    {
      key: 'security', label: 'Безопасность',
      children: (
        <>
          <Space wrap>
            <Form.Item label="Брандмауэр" name="firewallEnabled" valuePropName="checked" initialValue={true}>
              <Switch />
            </Form.Item>
            <Form.Item label="Профиль" name="firewallProfile" initialValue="Domain" style={{ width: 150 }}>
              <Select options={['Domain','Private','Public','All'].map(v => ({ value: v, label: v }))} />
            </Form.Item>
            <Form.Item label="Windows Defender" name="enableWindowsDefender" valuePropName="checked" initialValue={true}>
              <Switch />
            </Form.Item>
          </Space>
          <Button icon={<PlusOutlined />} type="dashed" size="small" style={{ marginBottom: 8 }}
            onClick={() => setFwRules(p => [...p, { _key: uid(), name: '', port: 80, proto: 'TCP', direction: 'Inbound' }])}>
            Открыть порт
          </Button>
          {fwRules.map(r => (
            <Space key={r._key} style={{ display: 'flex', marginBottom: 6 }}>
              <Input value={r.name} placeholder="Описание" style={{ width: 160 }}
                onChange={e => setFwRules(p => p.map(x => x._key === r._key ? { ...x, name: e.target.value } : x))} />
              <InputNumber value={r.port} min={1} max={65535} style={{ width: 90 }}
                onChange={v => setFwRules(p => p.map(x => x._key === r._key ? { ...x, port: v ?? 80 } : x))} />
              <Select value={r.proto} style={{ width: 80 }}
                options={['TCP','UDP'].map(v => ({ value: v, label: v }))}
                onChange={v => setFwRules(p => p.map(x => x._key === r._key ? { ...x, proto: v } : x))} />
              <Select value={r.direction} style={{ width: 110 }}
                options={['Inbound','Outbound'].map(v => ({ value: v, label: v }))}
                onChange={v => setFwRules(p => p.map(x => x._key === r._key ? { ...x, direction: v } : x))} />
              <Button size="small" danger type="text" icon={<DeleteOutlined />}
                onClick={() => setFwRules(p => p.filter(x => x._key !== r._key))} />
            </Space>
          ))}

          <Divider orientationMargin={0}>RDP</Divider>
          <Space wrap>
            <Form.Item label="Включить RDP" name="rdpEnabled" valuePropName="checked" initialValue={false}>
              <Switch />
            </Form.Item>
            <Form.Item label="NLA" name="rdpNla" valuePropName="checked" initialValue={true}>
              <Switch checkedChildren="NLA вкл" unCheckedChildren="NLA выкл" />
            </Form.Item>
            <Form.Item label="Порт RDP" name="rdpPort" initialValue={3389} style={{ width: 130 }}>
              <InputNumber min={1} max={65535} style={{ width: '100%' }} />
            </Form.Item>
          </Space>

          <Divider orientationMargin={0}>Парольная политика</Divider>
          <Space wrap>
            <Form.Item label="Мин. длина пароля" name={['passwordPolicy','minLength']} initialValue={8} style={{ width: 190 }}>
              <InputNumber min={0} max={20} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="Макс. возраст (дней)" name={['passwordPolicy','maxAge']} initialValue={90} style={{ width: 190 }}>
              <InputNumber min={0} max={999} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="Блокировка после N попыток" name={['passwordPolicy','lockoutThreshold']} initialValue={5} style={{ width: 230 }}>
              <InputNumber min={0} max={999} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="Длит. блокировки (мин)" name={['passwordPolicy','lockoutDuration']} initialValue={30} style={{ width: 200 }}>
              <InputNumber min={0} max={9999} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
        </>
      ),
    },
    {
      key: 'users', label: 'Пользователи',
      children: (
        <>
          <Button icon={<PlusOutlined />} type="dashed" style={{ marginBottom: 10 }}
            onClick={() => setLocalUsers(p => [...p, { _key: uid(), name: '', password: '', admin: false, disabled: false }])}>
            Добавить локального пользователя
          </Button>
          {localUsers.map(u => (
            <Space key={u._key} wrap style={{ display: 'flex', marginBottom: 8, padding: '8px', border: '1px solid #f0f0f0', borderRadius: 6 }}>
              <Input value={u.name} placeholder="username" style={{ width: 150 }}
                onChange={e => setLocalUsers(p => p.map(r => r._key === u._key ? { ...r, name: e.target.value } : r))} />
              <Input.Password value={u.password} placeholder="Пароль" style={{ width: 160 }}
                onChange={e => setLocalUsers(p => p.map(r => r._key === u._key ? { ...r, password: e.target.value } : r))} />
              <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <Switch size="small" checked={u.admin} onChange={v => setLocalUsers(p => p.map(r => r._key === u._key ? { ...r, admin: v } : r))} />
                <Text style={{ fontSize: 12 }}>Administrators</Text>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <Switch size="small" checked={u.disabled} onChange={v => setLocalUsers(p => p.map(r => r._key === u._key ? { ...r, disabled: v } : r))} />
                <Text style={{ fontSize: 12 }}>Disabled</Text>
              </div>
              <Button size="small" danger type="text" icon={<DeleteOutlined />}
                onClick={() => setLocalUsers(p => p.filter(r => r._key !== u._key))} />
            </Space>
          ))}

          <Divider orientationMargin={0}>Автовход</Divider>
          <Form.Item label="Включить автовход" name="autoLogonEnabled" valuePropName="checked" initialValue={false}>
            <Switch />
          </Form.Item>
          {autoLogonEnabled && (
            <Space wrap>
              <Form.Item label="Пользователь" name="autoLogonUser" rules={[{ required: true }]} style={{ minWidth: 200 }}>
                <Input />
              </Form.Item>
              <Form.Item label="Пароль" name="autoLogonPassword" rules={[{ required: true }]} style={{ minWidth: 200 }}>
                <Input.Password />
              </Form.Item>
            </Space>
          )}
        </>
      ),
    },
    {
      key: 'services', label: 'Службы и аудит',
      children: (
        <>
          <Button icon={<PlusOutlined />} type="dashed" style={{ marginBottom: 10 }}
            onClick={() => setServices(p => [...p, { _key: uid(), name: '', startType: 'Automatic', state: 'Running' }])}>
            Добавить службу
          </Button>
          {services.map(svc => (
            <Space key={svc._key} style={{ display: 'flex', marginBottom: 8 }}>
              <Input value={svc.name} placeholder="wuauserv" style={{ width: 200 }}
                onChange={e => setServices(p => p.map(r => r._key === svc._key ? { ...r, name: e.target.value } : r))} />
              <Select value={svc.startType} style={{ width: 140 }}
                options={['Automatic','Manual','Disabled'].map(v => ({ value: v, label: v }))}
                onChange={v => setServices(p => p.map(r => r._key === svc._key ? { ...r, startType: v } : r))} />
              <Select value={svc.state} style={{ width: 120 }}
                options={[{ value: 'Running', label: 'Running' }, { value: 'Stopped', label: 'Stopped' }]}
                onChange={v => setServices(p => p.map(r => r._key === svc._key ? { ...r, state: v } : r))} />
              <Button size="small" danger type="text" icon={<DeleteOutlined />}
                onClick={() => setServices(p => p.filter(r => r._key !== svc._key))} />
            </Space>
          ))}

          <Divider orientationMargin={0}>Мониторинг</Divider>
          <Form.Item label="Установить Zabbix Agent" name="installZabbixAgent" valuePropName="checked" initialValue={false}>
            <Switch />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(p, c) => p.installZabbixAgent !== c.installZabbixAgent}>
            {({ getFieldValue }) => getFieldValue('installZabbixAgent') && (
              <Form.Item label="Zabbix Server" name="zabbixServer">
                <Input placeholder="192.168.1.50" style={{ maxWidth: 260 }} />
              </Form.Item>
            )}
          </Form.Item>

          <Divider orientationMargin={0}>Политика аудита</Divider>
          <Button icon={<PlusOutlined />} type="dashed" size="small" style={{ marginBottom: 8 }}
            onClick={() => setAuditCats(p => [...p, { _key: uid(), name: 'Logon' }])}>
            Добавить категорию
          </Button>
          {auditCats.map(a => (
            <Space key={a._key} style={{ display: 'flex', marginBottom: 6 }}>
              <Select value={a.name} style={{ width: 280 }}
                options={['Logon','Logoff','Account Logon','Object Access','Policy Change','Privilege Use','System','Account Management'].map(v => ({ value: v, label: v }))}
                onChange={v => setAuditCats(p => p.map(r => r._key === a._key ? { ...r, name: v } : r))} />
              <Button size="small" danger type="text" icon={<DeleteOutlined />}
                onClick={() => setAuditCats(p => p.filter(r => r._key !== a._key))} />
            </Space>
          ))}
        </>
      ),
    },
    {
      key: 'connection', label: 'WinRM',
      children: (
        <>
          <Alert type="info" showIcon style={{ marginBottom: 12 }}
            message="Подключение через WinRM. Порт 5985 = HTTP, 5986 = HTTPS. Учётные данные не сохраняются." />
          <Space wrap>
            <Form.Item label="Логин" name="winrmUsername" rules={[{ required: true }]} style={{ minWidth: 220 }}>
              <Input placeholder="DOMAIN\\admin или .\\Administrator" />
            </Form.Item>
            <Form.Item label="Пароль" name="winrmPassword" rules={[{ required: true }]} style={{ minWidth: 200 }}>
              <Input.Password />
            </Form.Item>
            <Form.Item label="Порт" name="winrmPort" initialValue={5985} style={{ width: 120 }}>
              <InputNumber min={1} max={65535} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
        </>
      ),
    },
  ];

  return (
    <Modal
      title={`Windows — ${hostname || 'устройство'}`}
      open={open} onCancel={onClose} footer={null} width={820} destroyOnClose
    >
      <Form form={form} layout="vertical" autoComplete="off">
        <Tabs type="card" items={tabItems} />

        <div style={{ marginTop: 16 }}>
          <SaveToMemoryToggle
            value={saveToMemory}
            onChange={setSaveToMemory}
            onLabel="Создать точку восстановления"
            offLabel="Применить без резервной копии"
            onDescription="Перед применением создаётся System Restore Point. При сбое: Панель управления → Восстановление системы."
            offDescription="Изменения применяются без создания точки восстановления — используйте в изолированных или тестовых средах."
          />
        </div>

        <Button type="primary" icon={<ThunderboltOutlined />} loading={submitting}
          onClick={handleSubmit} block style={{ marginTop: 16 }}>
          Применить конфигурацию
        </Button>
      </Form>
    </Modal>
  );
}
