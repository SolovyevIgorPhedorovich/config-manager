import React, { useState } from 'react';
import {
  Modal, Form, Input, InputNumber, Select, Switch, Button, Tabs,
  Divider, Space, Alert, Tooltip, Tag, Table, Collapse,
  Typography, Badge, message,
} from 'antd';
import {
  PlusOutlined, DeleteOutlined, SaveOutlined, ThunderboltOutlined,
  InfoCircleOutlined, WarningOutlined, CheckCircleOutlined,
} from '@ant-design/icons';
import { configApi } from '../api/configApi';
import ConfigPrefillBar from './ConfigPrefillBar';

const { Text } = Typography;

// ─────────────────────────────────────────────────────────────────────────────
// Типы
// ─────────────────────────────────────────────────────────────────────────────

interface InterfaceRow {
  _key: string;
  name: string;
  description: string;
  role: 'access_endpoint' | 'uplink' | 'server' | 'management' | 'disabled';
  mode: 'access' | 'trunk';
  vlanId: number;
  nativeVlan: number;
  allowedVlans: string;
  speed: string;
  duplex: string;
  shutdown: boolean;
  portfast: boolean;
  bpduGuard: boolean;
  bpduFilter: boolean;
  portSecurity: boolean;
  stormControl: boolean;
  stormControlLevel: number;
  dhcpTrusted: boolean;
}

interface VlanRow   { _key: string; id: number; name: string }
interface SviRow    { _key: string; vlan: number; ipAddress: string; subnetMask: string; description: string }
interface RouteRow  { _key: string; network: string; mask: string; nextHop: string }

interface Props {
  open: boolean;
  onClose: () => void;
  hostname?: string;
  deviceId?: number;
}

// ─────────────────────────────────────────────────────────────────────────────
// Константы
// ─────────────────────────────────────────────────────────────────────────────

const INTERFACE_OPTIONS = [
  'GigabitEthernet0/0','GigabitEthernet0/1','GigabitEthernet0/2','GigabitEthernet0/3',
  'GigabitEthernet0/4','GigabitEthernet0/5','GigabitEthernet0/6','GigabitEthernet0/7',
  'GigabitEthernet1/0','GigabitEthernet1/1',
  'FastEthernet0/0','FastEthernet0/1','FastEthernet0/24',
  'TenGigabitEthernet0/1','TenGigabitEthernet0/2',
  'Port-channel1','Port-channel2','Management0/0',
];

const ROLE_LABELS: Record<string, string> = {
  access_endpoint: 'Оконечное устройство (ПК, принтер, IP-камера)',
  uplink:          'Аплинк (связь с вышестоящим коммутатором)',
  server:          'Серверный порт',
  management:      'Управление (Management VLAN)',
  disabled:        'Отключён',
};

const ROLE_PRESETS: Record<string, Partial<InterfaceRow>> = {
  access_endpoint: { mode: 'access', portfast: true,  bpduGuard: true,  portSecurity: true,  dhcpTrusted: false, shutdown: false },
  uplink:          { mode: 'trunk',  portfast: false, bpduGuard: false, portSecurity: false, dhcpTrusted: true,  shutdown: false },
  server:          { mode: 'access', portfast: false, bpduGuard: false, portSecurity: false, dhcpTrusted: false, shutdown: false },
  management:      { mode: 'access', portfast: false, bpduGuard: false, portSecurity: false, dhcpTrusted: false, shutdown: false },
  disabled:        { shutdown: true },
};

const SYSLOG_LEVELS = ['emergencies','alerts','critical','errors','warnings','notifications','informational','debugging'];

const MASK_OPTIONS = ['255.255.255.0','255.255.255.128','255.255.255.192','255.255.255.224',
                      '255.255.254.0','255.255.252.0','255.255.0.0','255.0.0.0'];

function uid() { return Math.random().toString(36).slice(2, 8); }

function emptyIface(): InterfaceRow {
  return { _key: uid(), name: 'GigabitEthernet0/1', description: '', role: 'access_endpoint',
           mode: 'access', vlanId: 10, nativeVlan: 1, allowedVlans: '', speed: 'auto', duplex: 'auto',
           shutdown: false, portfast: true, bpduGuard: true, bpduFilter: false,
           portSecurity: true, stormControl: false, stormControlLevel: 10, dhcpTrusted: false };
}

// ─────────────────────────────────────────────────────────────────────────────
// Компонент
// ─────────────────────────────────────────────────────────────────────────────

export default function ConfigCiscoModal({ open, onClose, hostname, deviceId }: Props) {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [saveToMemory, setSaveToMemory] = useState(false);

  // Управляемые списки (Form.List для интерфейсов оказывается громоздким с Collapse)
  const [interfaces, setInterfaces] = useState<InterfaceRow[]>([emptyIface()]);
  const [vlans,      setVlans]      = useState<VlanRow[]>([]);
  const [svis,       setSvis]       = useState<SviRow[]>([]);
  const [routes,     setRoutes]     = useState<RouteRow[]>([]);

  const updateIface = (key: string, patch: Partial<InterfaceRow>) =>
    setInterfaces(prev => prev.map(r => r._key === key ? { ...r, ...patch } : r));

  const applyRolePreset = (key: string, role: string) => {
    const preset = ROLE_PRESETS[role] ?? {};
    updateIface(key, { role: role as InterfaceRow['role'], ...preset });
  };

  // Префилл значений из текущей конфигурации устройства или из шаблона
  const prefill = (src: Record<string, any>) => {
    const { interfaces: ifs, vlans: vl, sviInterfaces: sv, staticRoutes: rt, ...scalars } = src;
    form.setFieldsValue(scalars);
    if (Array.isArray(ifs))  setInterfaces(ifs.map((i: any) => ({ ...emptyIface(), ...i, _key: uid() })));
    if (Array.isArray(vl))   setVlans(vl.map((v: any) => ({ _key: uid(), id: 10, name: '', ...v })));
    if (Array.isArray(sv))   setSvis(sv.map((s: any) => ({ _key: uid(), vlan: 10, ipAddress: '', subnetMask: '255.255.255.0', description: '', ...s })));
    if (Array.isArray(rt))   setRoutes(rt.map((r: any) => ({ _key: uid(), network: '', mask: '255.255.255.0', nextHop: '', ...r })));
    if (typeof src.saveToMemory === 'boolean') setSaveToMemory(src.saveToMemory);
  };

  // ── Сабмит ────────────────────────────────────────────────────────────────

  const handleSubmit = async () => {
    if (!deviceId) return message.error('Устройство не выбрано');
    let fields: Record<string, any>;
    try { fields = await form.validateFields(); } catch { return; }

    const configData = {
      ...fields,
      interfaces: interfaces.map(({ _key, ...rest }) => rest),
      vlans:      vlans.map(({ _key, ...r }) => r),
      sviInterfaces: svis.map(({ _key, ...r }) => r),
      staticRoutes:  routes.map(({ _key, ...r }) => r),
      saveToMemory,
    };

    // credentials передаются отдельно
    const { sshUsername, sshPassword, sshPort, enablePassword, ...config } =
      configData as Record<string, any>;

    setSubmitting(true);
    try {
      const res = await configApi.applyConfig({
        deviceIds: [deviceId],
        configData: config,
        credentials: {
          [deviceId]: { username: sshUsername, password: sshPassword, port: sshPort ?? 22 },
        },
      });
      const outcome = await configApi.resolveApplyOutcome(res.data);
      if (outcome.kind === 'failed') {
        message.error('Не удалось применить конфигурацию: ' + (outcome.error || 'устройство недоступно'));
        return; // оставляем окно открытым для повторной попытки
      }
      if (outcome.kind === 'scheduled') {
        message.warning('Устройство офлайн — конфигурация применится автоматически при появлении в сети');
      } else if (outcome.kind === 'pending') {
        message.info('Применение запущено и выполняется в фоне — проверьте статус позже');
      } else {
        message.success(
          saveToMemory
            ? 'Конфигурация применена и сохранена в NVRAM (write memory)'
            : 'Конфигурация применена в running-config (без сохранения в NVRAM)'
        );
      }
      onClose();
    } catch (e: any) {
      message.error(e?.response?.data?.message || 'Ошибка применения конфигурации');
    } finally {
      setSubmitting(false);
    }
  };

  // ── Табы ──────────────────────────────────────────────────────────────────

  const tabItems = [

    // ── 1. Система ───────────────────────────────────────────────────────────
    {
      key: 'system',
      label: 'Система',
      children: (
        <>
          <Space wrap style={{ width: '100%' }}>
            <Form.Item label="Hostname" name="hostname" style={{ minWidth: 220 }}>
              <Input placeholder="SW1" />
            </Form.Item>
            <Form.Item label="Domain Name" name="domainName" style={{ minWidth: 220 }}>
              <Input placeholder="example.local" />
            </Form.Item>
          </Space>

          <Form.Item label="Enable Secret" name="enableSecret">
            <Input.Password placeholder="Пароль привилегированного режима" />
          </Form.Item>

          <Form.Item label="Banner MOTD" name="bannerMotd">
            <Input.TextArea rows={3} placeholder="Authorized access only. Disconnect if unauthorized." />
          </Form.Item>

          <Divider>SSH / VTY</Divider>
          <Space wrap>
            <Form.Item label="SSH Version" name="sshVersion" initialValue={2} style={{ width: 140 }}>
              <Select options={[{ value: 1, label: 'v1' }, { value: 2, label: 'v2 (рекомендуется)' }]} />
            </Form.Item>
            <Form.Item label="SSH Timeout (сек)" name="sshTimeout" initialValue={60} style={{ width: 180 }}>
              <InputNumber min={10} max={120} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="VTY Idle Timeout (мин)" name="vtyTimeout" initialValue={10} style={{ width: 200 }}>
              <InputNumber min={0} max={60} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.Item label="VTY ACL (входящий)" name="vtyAclIn" tooltip="Имя ACL, ограничивающего доступ к VTY">
            <Input placeholder="SSH_ACCESS" style={{ maxWidth: 260 }} />
          </Form.Item>
        </>
      ),
    },

    // ── 2. Интерфейсы ────────────────────────────────────────────────────────
    {
      key: 'interfaces',
      label: `Интерфейсы (${interfaces.length})`,
      children: (
        <>
          <Button
            icon={<PlusOutlined />}
            type="dashed"
            style={{ marginBottom: 12 }}
            onClick={() => setInterfaces(prev => [...prev, emptyIface()])}
          >
            Добавить интерфейс
          </Button>

          <Collapse
            accordion={false}
            items={interfaces.map((iface, idx) => ({
              key: iface._key,
              label: (
                <Space>
                  <Text strong style={{ fontFamily: 'monospace' }}>{iface.name}</Text>
                  {iface.description && <Text type="secondary">— {iface.description}</Text>}
                  <Tag color={
                    iface.role === 'uplink' ? 'blue' :
                    iface.role === 'server' ? 'purple' :
                    iface.role === 'management' ? 'gold' :
                    iface.role === 'disabled' ? 'red' : 'green'
                  }>
                    {ROLE_LABELS[iface.role]?.split(' ')[0] ?? iface.role}
                  </Tag>
                  {iface.shutdown && <Tag color="red">shutdown</Tag>}
                </Space>
              ),
              extra: (
                <Button
                  size="small" danger type="text" icon={<DeleteOutlined />}
                  onClick={e => { e.stopPropagation(); setInterfaces(p => p.filter(r => r._key !== iface._key)); }}
                />
              ),
              children: <InterfaceEditor iface={iface} onChange={p => updateIface(iface._key, p)} onRoleChange={r => applyRolePreset(iface._key, r)} />,
            }))}
          />
        </>
      ),
    },

    // ── 3. VLAN ──────────────────────────────────────────────────────────────
    {
      key: 'vlan',
      label: `VLAN (${vlans.length})`,
      children: (
        <>
          <Button icon={<PlusOutlined />} type="dashed" style={{ marginBottom: 12 }}
            onClick={() => setVlans(p => [...p, { _key: uid(), id: 10, name: '' }])}>
            Добавить VLAN
          </Button>
          {vlans.map(v => (
            <Space key={v._key} style={{ display: 'flex', marginBottom: 8 }}>
              <InputNumber min={1} max={4094} value={v.id} placeholder="ID" style={{ width: 90 }}
                onChange={val => setVlans(p => p.map(r => r._key === v._key ? { ...r, id: val ?? 1 } : r))} />
              <Input value={v.name} placeholder="Название (Management, Servers…)" style={{ width: 240 }}
                onChange={e => setVlans(p => p.map(r => r._key === v._key ? { ...r, name: e.target.value } : r))} />
              <Button size="small" danger type="text" icon={<DeleteOutlined />}
                onClick={() => setVlans(p => p.filter(r => r._key !== v._key))} />
            </Space>
          ))}

          <Divider>SVI (VLAN-интерфейсы с IP)</Divider>
          <Button icon={<PlusOutlined />} type="dashed" style={{ marginBottom: 12 }}
            onClick={() => setSvis(p => [...p, { _key: uid(), vlan: 10, ipAddress: '', subnetMask: '255.255.255.0', description: '' }])}>
            Добавить SVI
          </Button>
          {svis.map(s => (
            <Space key={s._key} wrap style={{ display: 'flex', marginBottom: 8 }}>
              <InputNumber min={1} max={4094} value={s.vlan} placeholder="VLAN" style={{ width: 80 }}
                onChange={val => setSvis(p => p.map(r => r._key === s._key ? { ...r, vlan: val ?? 1 } : r))} />
              <Input value={s.ipAddress} placeholder="192.168.10.1" style={{ width: 150 }}
                onChange={e => setSvis(p => p.map(r => r._key === s._key ? { ...r, ipAddress: e.target.value } : r))} />
              <Select value={s.subnetMask} style={{ width: 160 }}
                options={MASK_OPTIONS.map(m => ({ value: m, label: m }))}
                onChange={val => setSvis(p => p.map(r => r._key === s._key ? { ...r, subnetMask: val } : r))} />
              <Input value={s.description} placeholder="Описание" style={{ width: 180 }}
                onChange={e => setSvis(p => p.map(r => r._key === s._key ? { ...r, description: e.target.value } : r))} />
              <Button size="small" danger type="text" icon={<DeleteOutlined />}
                onClick={() => setSvis(p => p.filter(r => r._key !== s._key))} />
            </Space>
          ))}
        </>
      ),
    },

    // ── 4. Маршрутизация ─────────────────────────────────────────────────────
    {
      key: 'routing',
      label: 'Маршрутизация',
      children: (
        <>
          <Form.Item label="Шлюз по умолчанию" name="defaultGateway" tooltip="ip default-gateway">
            <Input placeholder="192.168.1.254" style={{ maxWidth: 220 }} />
          </Form.Item>

          <Divider>Статические маршруты</Divider>
          <Button icon={<PlusOutlined />} type="dashed" style={{ marginBottom: 12 }}
            onClick={() => setRoutes(p => [...p, { _key: uid(), network: '', mask: '255.255.255.0', nextHop: '' }])}>
            Добавить маршрут
          </Button>
          {routes.map(r => (
            <Space key={r._key} wrap style={{ display: 'flex', marginBottom: 8 }}>
              <Input value={r.network} placeholder="Сеть (10.0.0.0)" style={{ width: 150 }}
                onChange={e => setRoutes(p => p.map(x => x._key === r._key ? { ...x, network: e.target.value } : x))} />
              <Select value={r.mask} style={{ width: 160 }}
                options={MASK_OPTIONS.map(m => ({ value: m, label: m }))}
                onChange={val => setRoutes(p => p.map(x => x._key === r._key ? { ...x, mask: val } : x))} />
              <Input value={r.nextHop} placeholder="Next-hop IP" style={{ width: 150 }}
                onChange={e => setRoutes(p => p.map(x => x._key === r._key ? { ...x, nextHop: e.target.value } : x))} />
              <Button size="small" danger type="text" icon={<DeleteOutlined />}
                onClick={() => setRoutes(p => p.filter(x => x._key !== r._key))} />
            </Space>
          ))}
        </>
      ),
    },

    // ── 5. Сервисы ───────────────────────────────────────────────────────────
    {
      key: 'services',
      label: 'Сервисы',
      children: (
        <>
          <Divider orientationMargin={0}>NTP</Divider>
          <Space wrap>
            <Form.Item label="NTP-сервер" name="ntpServer" style={{ minWidth: 220 }}>
              <Input placeholder="192.168.1.100" />
            </Form.Item>
            <Form.Item label="Source interface" name="ntpSourceInterface" style={{ minWidth: 180 }}>
              <Input placeholder="Vlan10" />
            </Form.Item>
          </Space>

          <Divider orientationMargin={0}>SNMP</Divider>
          <Form.Item label="Включить SNMP" name="snmpEnabled" valuePropName="checked" initialValue={false}>
            <Switch />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(p, c) => p.snmpEnabled !== c.snmpEnabled}>
            {({ getFieldValue }) => getFieldValue('snmpEnabled') && (
              <Space wrap>
                <Form.Item label="Community RO" name="snmpCommunityRo" style={{ minWidth: 180 }}>
                  <Input placeholder="public" />
                </Form.Item>
                <Form.Item label="Community RW" name="snmpCommunityRw" style={{ minWidth: 180 }}>
                  <Input placeholder="private" />
                </Form.Item>
                <Form.Item label="SNMP-сервер" name="snmpServer" style={{ minWidth: 180 }}>
                  <Input placeholder="192.168.1.50" />
                </Form.Item>
              </Space>
            )}
          </Form.Item>

          <Divider orientationMargin={0}>Syslog</Divider>
          <Space wrap>
            <Form.Item label="Syslog-сервер" name="syslogServer" style={{ minWidth: 220 }}>
              <Input placeholder="192.168.1.60" />
            </Form.Item>
            <Form.Item label="Уровень" name="syslogLevel" initialValue="warnings" style={{ minWidth: 180 }}>
              <Select options={SYSLOG_LEVELS.map(l => ({ value: l, label: l }))} />
            </Form.Item>
          </Space>

          <Divider orientationMargin={0}>DHCP Snooping</Divider>
          <Form.Item label="Включить DHCP Snooping" name="dhcpSnoopingEnabled" valuePropName="checked" initialValue={false}>
            <Switch />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(p, c) => p.dhcpSnoopingEnabled !== c.dhcpSnoopingEnabled}>
            {({ getFieldValue }) => getFieldValue('dhcpSnoopingEnabled') && (
              <Form.Item label="VLAN список" name="dhcpSnoopingVlans" tooltip="Пример: 10,20,30-40">
                <Input placeholder="10,20,30" style={{ maxWidth: 260 }} />
              </Form.Item>
            )}
          </Form.Item>
        </>
      ),
    },

    // ── 6. Подключение ───────────────────────────────────────────────────────
    {
      key: 'connection',
      label: 'Подключение',
      children: (
        <>
          <Alert
            type="info" showIcon style={{ marginBottom: 16 }}
            message="Учётные данные используются только для подключения по SSH и не сохраняются на сервере."
          />
          <Space wrap>
            <Form.Item label="SSH-логин" name="sshUsername"
              rules={[{ required: true, message: 'Введите логин' }]} style={{ minWidth: 200 }}>
              <Input placeholder="admin" />
            </Form.Item>
            <Form.Item label="SSH-пароль" name="sshPassword"
              rules={[{ required: true, message: 'Введите пароль' }]} style={{ minWidth: 200 }}>
              <Input.Password placeholder="••••••" />
            </Form.Item>
            <Form.Item label="Порт" name="sshPort" initialValue={22} style={{ width: 110 }}>
              <InputNumber min={1} max={65535} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.Item label="Enable-пароль" name="enablePassword"
            tooltip="Пароль для перехода в привилегированный режим (если отличается от SSH)">
            <Input.Password placeholder="Необязательно" style={{ maxWidth: 260 }} />
          </Form.Item>
        </>
      ),
    },
  ];

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <Modal
      title={
        <Space>
          <span style={{ fontFamily: 'monospace', color: '#fa8b0f' }}>⚙</span>
          <span>Cisco IOS — {hostname || 'устройство'}</span>
        </Space>
      }
      open={open}
      onCancel={onClose}
      width={820}
      destroyOnClose
      footer={null}
    >
      <Form form={form} layout="vertical" autoComplete="off">
        <ConfigPrefillBar open={open} deviceId={deviceId} deviceHostname={hostname} onPrefill={prefill} />
        <Tabs type="card" items={tabItems} />

        {/* ── Опция сохранения в NVRAM ───────────────────────────────────── */}
        <div style={{
          marginTop: 20,
          padding: '14px 16px',
          border: `2px solid ${saveToMemory ? '#52c41a' : '#faad14'}`,
          borderRadius: 8,
          background: saveToMemory ? '#f6ffed' : '#fffbe6',
          transition: 'all 0.2s',
        }}>
          <Space align="start" style={{ width: '100%', justifyContent: 'space-between' }}>
            <Space align="start">
              {saveToMemory
                ? <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 20, marginTop: 2 }} />
                : <WarningOutlined style={{ color: '#faad14', fontSize: 20, marginTop: 2 }} />
              }
              <div>
                <Text strong style={{ fontSize: 14 }}>
                  {saveToMemory ? 'Сохранить в NVRAM (write memory)' : 'Только в running-config (без сохранения)'}
                </Text>
                <br />
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {saveToMemory
                    ? 'Конфигурация будет сохранена постоянно. При перезагрузке устройство восстановится с этими настройками.'
                    : 'Настройки применятся только в оперативную память. При перезагрузке устройство вернётся к предыдущей конфигурации — удобно для тестирования.'}
                </Text>
              </div>
            </Space>
            <Switch
              checked={saveToMemory}
              onChange={setSaveToMemory}
              checkedChildren={<SaveOutlined />}
              unCheckedChildren="RAM"
              style={{ marginLeft: 12, flexShrink: 0 }}
            />
          </Space>
        </div>

        <Button
          type="primary"
          icon={<ThunderboltOutlined />}
          loading={submitting}
          onClick={handleSubmit}
          block
          style={{ marginTop: 16 }}
        >
          Применить конфигурацию
        </Button>
      </Form>
    </Modal>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Редактор одного интерфейса
// ─────────────────────────────────────────────────────────────────────────────

interface EditorProps {
  iface: InterfaceRow;
  onChange: (patch: Partial<InterfaceRow>) => void;
  onRoleChange: (role: string) => void;
}

function InterfaceEditor({ iface, onChange, onRoleChange }: EditorProps) {
  const isTrunk = iface.mode === 'trunk';

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>

      {/* Имя + описание + роль */}
      <Space wrap>
        <div>
          <Text type="secondary" style={{ fontSize: 12 }}>Интерфейс</Text>
          <Select
            showSearch value={iface.name} style={{ width: 230, display: 'block' }}
            options={INTERFACE_OPTIONS.map(n => ({ value: n, label: n }))}
            onChange={v => onChange({ name: v })}
            dropdownRender={menu => (
              <>{menu}<Divider style={{ margin: '4px 0' }} />
                <div style={{ padding: '4px 8px' }}>
                  <Text type="secondary" style={{ fontSize: 11 }}>
                    Введите имя вручную и нажмите Enter
                  </Text>
                </div>
              </>
            )}
          />
        </div>

        <div>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Описание порта{' '}
            <Tooltip title="Отображается в конфиге как «description». Помогает идентифицировать назначение порта.">
              <InfoCircleOutlined style={{ color: '#1677ff' }} />
            </Tooltip>
          </Text>
          <Input
            value={iface.description} style={{ width: 260, display: 'block' }}
            placeholder="Например: PC-Ivan Ivanov / Uplink to CoreSW"
            onChange={e => onChange({ description: e.target.value })}
          />
        </div>
      </Space>

      <div>
        <Text type="secondary" style={{ fontSize: 12 }}>
          Роль в сети{' '}
          <Tooltip title="Роль автоматически применяет типовые настройки безопасности для данного типа подключения.">
            <InfoCircleOutlined style={{ color: '#1677ff' }} />
          </Tooltip>
        </Text>
        <Select
          value={iface.role} style={{ width: '100%', display: 'block' }}
          options={Object.entries(ROLE_LABELS).map(([v, l]) => ({ value: v, label: l }))}
          onChange={onRoleChange}
        />
      </div>

      {iface.role !== 'disabled' && (
        <>
          {/* Режим коммутации */}
          <Divider style={{ margin: '4px 0', fontSize: 13 }}>Коммутация</Divider>
          <Space wrap>
            <div>
              <Text type="secondary" style={{ fontSize: 12 }}>Режим</Text>
              <Select value={iface.mode} style={{ width: 130, display: 'block' }}
                options={[{ value: 'access', label: 'Access' }, { value: 'trunk', label: 'Trunk' }]}
                onChange={v => onChange({ mode: v })} />
            </div>

            {isTrunk ? (
              <>
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>Native VLAN</Text>
                  <InputNumber min={1} max={4094} value={iface.nativeVlan} style={{ width: 110, display: 'block' }}
                    onChange={v => onChange({ nativeVlan: v ?? 1 })} />
                </div>
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    Allowed VLANs{' '}
                    <Tooltip title="Пример: 10,20,30-40. Пустое поле = all.">
                      <InfoCircleOutlined style={{ color: '#1677ff' }} />
                    </Tooltip>
                  </Text>
                  <Input value={iface.allowedVlans} placeholder="10,20,30-40" style={{ width: 180, display: 'block' }}
                    onChange={e => onChange({ allowedVlans: e.target.value })} />
                </div>
              </>
            ) : (
              <div>
                <Text type="secondary" style={{ fontSize: 12 }}>Access VLAN</Text>
                <InputNumber min={1} max={4094} value={iface.vlanId} style={{ width: 110, display: 'block' }}
                  onChange={v => onChange({ vlanId: v ?? 1 })} />
              </div>
            )}
          </Space>

          {/* Физические параметры */}
          <Divider style={{ margin: '4px 0', fontSize: 13 }}>Физика</Divider>
          <Space wrap>
            <div>
              <Text type="secondary" style={{ fontSize: 12 }}>Speed</Text>
              <Select value={iface.speed} style={{ width: 130, display: 'block' }}
                options={['auto','10','100','1000','10000'].map(v => ({ value: v, label: v === 'auto' ? 'Auto' : `${v} Mbps` }))}
                onChange={v => onChange({ speed: v })} />
            </div>
            <div>
              <Text type="secondary" style={{ fontSize: 12 }}>Duplex</Text>
              <Select value={iface.duplex} style={{ width: 130, display: 'block' }}
                options={['auto','full','half'].map(v => ({ value: v, label: v.charAt(0).toUpperCase() + v.slice(1) }))}
                onChange={v => onChange({ duplex: v })} />
            </div>
            <div>
              <Text type="secondary" style={{ fontSize: 12 }}>Состояние</Text>
              <div>
                <Switch
                  checked={!iface.shutdown}
                  checkedChildren="no shutdown" unCheckedChildren="shutdown"
                  onChange={v => onChange({ shutdown: !v })}
                />
              </div>
            </div>
          </Space>

          {/* Безопасность */}
          <Divider style={{ margin: '4px 0', fontSize: 13 }}>Безопасность (STP / Port Security)</Divider>
          <Space wrap>
            {!isTrunk && (
              <>
                <Tooltip title="spanning-tree portfast — ускоряет переход порта в Forwarding">
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <Switch size="small" checked={iface.portfast} onChange={v => onChange({ portfast: v })} />
                    <Text style={{ fontSize: 13 }}>Portfast</Text>
                  </div>
                </Tooltip>
                <Tooltip title="switchport port-security — ограничивает MAC-адреса на порту">
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <Switch size="small" checked={iface.portSecurity} onChange={v => onChange({ portSecurity: v })} />
                    <Text style={{ fontSize: 13 }}>Port Security</Text>
                  </div>
                </Tooltip>
              </>
            )}
            <Tooltip title="spanning-tree bpduguard enable — блокирует порт при получении BPDU">
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <Switch size="small" checked={iface.bpduGuard} onChange={v => onChange({ bpduGuard: v })} />
                <Text style={{ fontSize: 13 }}>BPDU Guard</Text>
              </div>
            </Tooltip>
            <Tooltip title="spanning-tree bpdufilter enable — не отправляет и не обрабатывает BPDU">
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <Switch size="small" checked={iface.bpduFilter} onChange={v => onChange({ bpduFilter: v })} />
                <Text style={{ fontSize: 13 }}>BPDU Filter</Text>
              </div>
            </Tooltip>
            <Tooltip title="ip dhcp snooping trust — доверенный порт для DHCP Snooping (аплинки)">
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <Switch size="small" checked={iface.dhcpTrusted} onChange={v => onChange({ dhcpTrusted: v })} />
                <Text style={{ fontSize: 13 }}>DHCP Trusted</Text>
              </div>
            </Tooltip>
          </Space>

          {/* Storm Control */}
          <Space wrap style={{ marginTop: 4 }}>
            <Tooltip title="storm-control broadcast/multicast level — защита от широковещательных штормов">
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <Switch size="small" checked={iface.stormControl} onChange={v => onChange({ stormControl: v })} />
                <Text style={{ fontSize: 13 }}>Storm Control</Text>
              </div>
            </Tooltip>
            {iface.stormControl && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <Text type="secondary" style={{ fontSize: 13 }}>Порог:</Text>
                <InputNumber
                  min={1} max={100} value={iface.stormControlLevel}
                  onChange={v => onChange({ stormControlLevel: v ?? 10 })}
                  formatter={v => `${v}%`} style={{ width: 80 }}
                  size="small"
                />
              </div>
            )}
          </Space>
        </>
      )}

      {iface.role === 'disabled' && (
        <Alert type="warning" showIcon message="Интерфейс будет переведён в состояние shutdown" />
      )}
    </div>
  );
}
