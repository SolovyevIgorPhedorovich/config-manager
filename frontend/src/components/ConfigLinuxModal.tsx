import React, { useState } from 'react';
import {
  Modal, Form, Input, InputNumber, Select, Switch, Button, Tabs,
  Divider, Space, Tooltip, Typography, message,
} from 'antd';
import { PlusOutlined, DeleteOutlined, ThunderboltOutlined, InfoCircleOutlined } from '@ant-design/icons';
import { configApi } from '../api/configApi';
import SaveToMemoryToggle from './SaveToMemoryToggle';
import ConfigPrefillBar from './ConfigPrefillBar';

const { Text } = Typography;

interface NetworkIface { _key: string; name: string; address: string; prefix: string; gateway: string }
interface SysctlParam  { _key: string; key: string; value: string }
interface ServiceItem  { _key: string; name: string; enabled: boolean; running: boolean }
interface UserItem     { _key: string; name: string; password: string; sudo: boolean; locked: boolean; create: boolean }
interface CronItem     { _key: string; schedule: string; command: string; user: string }
interface FwPort       { _key: string; port: number; proto: string }

function uid() { return Math.random().toString(36).slice(2, 8); }

interface Props { open: boolean; onClose: () => void; hostname?: string; deviceId?: number }

const TIMEZONES = ['UTC','Europe/Moscow','Europe/London','Europe/Berlin','America/New_York','Asia/Tokyo','Asia/Almaty','Asia/Yekaterinburg'];

export default function ConfigLinuxModal({ open, onClose, hostname, deviceId }: Props) {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [saveToMemory, setSaveToMemory] = useState(true);

  const [ifaces,   setIfaces]   = useState<NetworkIface[]>([]);
  const [sysctls,  setSysctls]  = useState<SysctlParam[]>([]);
  const [services, setServices] = useState<ServiceItem[]>([]);
  const [users,    setUsers]    = useState<UserItem[]>([]);
  const [crons,    setCrons]    = useState<CronItem[]>([]);
  const [fwPorts,  setFwPorts]  = useState<FwPort[]>([]);

  const handleSubmit = async () => {
    if (!deviceId) return message.error('Устройство не выбрано');
    let fields: Record<string, any>;
    try { fields = await form.validateFields(); } catch { return; }

    const { sshUsername, sshPassword, sshPort, ...configFields } = fields as Record<string, any>;
    const configData = {
      ...configFields,
      networkInterfaces: ifaces.map(({ _key, ...r }) => r),
      sysctlParams:  sysctls.map(({ _key, ...r }) => r),
      services:      services.map(({ _key, ...r }) => r),
      users:         users.map(({ _key, ...r }) => r),
      cronJobs:      crons.map(({ _key, ...r }) => r),
      firewallOpenPorts: fwPorts.map(({ _key, ...r }) => r),
      saveToMemory,
    };

    setSubmitting(true);
    try {
      const res = await configApi.applyConfig({
        deviceIds: [deviceId],
        configData,
        credentials: { [deviceId]: { username: sshUsername, password: sshPassword, port: sshPort ?? 22 } },
      });
      if (res.data?.scheduledDeviceIds?.length) {
        message.warning('Устройство офлайн — конфигурация применится автоматически при появлении в сети');
      } else {
        message.success(saveToMemory ? 'Конфигурация применена и сохранена' : 'Конфигурация применена (только текущий сеанс)');
      }
      onClose();
    } catch (e: any) {
      message.error(e?.response?.data?.message || 'Ошибка применения конфигурации');
    } finally {
      setSubmitting(false);
    }
  };

  // Префилл значений из текущей конфигурации устройства или из шаблона
  const prefill = (src: Record<string, any>) => {
    const { networkInterfaces, sysctlParams, services: svc, users: us, cronJobs, firewallOpenPorts,
            saveToMemory: stm, ...scalars } = src as Record<string, any>;
    form.setFieldsValue(scalars);
    if (Array.isArray(networkInterfaces))
      setIfaces(networkInterfaces.map((r: any) => ({ _key: uid(), name: 'eth0', address: '', prefix: '24', gateway: '', ...r })));
    if (Array.isArray(sysctlParams))
      setSysctls(sysctlParams.map((r: any) => ({ _key: uid(), key: '', value: '', ...r })));
    if (Array.isArray(svc))
      setServices(svc.map((r: any) => ({ _key: uid(), name: '', enabled: false, running: false, ...r })));
    if (Array.isArray(us))
      setUsers(us.map((u: any) => ({ _key: uid(), name: '', password: '', sudo: false, locked: false, create: true, ...u })));
    if (Array.isArray(cronJobs))
      setCrons(cronJobs.map((c: any) => ({ _key: uid(), schedule: '', command: '', user: 'root', ...c })));
    if (Array.isArray(firewallOpenPorts))
      setFwPorts(firewallOpenPorts.map((p: any) => ({ _key: uid(), port: 80, proto: 'tcp', ...p })));
    if (typeof stm === 'boolean') setSaveToMemory(stm);
  };

  const tabItems = [
    {
      key: 'system', label: 'Система',
      children: (
        <>
          <Space wrap>
            <Form.Item label="Hostname" name="hostname" style={{ minWidth: 220 }}>
              <Input placeholder="web-01.example.com" />
            </Form.Item>
            <Form.Item label="Часовой пояс" name="timezone" initialValue="Europe/Moscow" style={{ minWidth: 220 }}>
              <Select showSearch options={TIMEZONES.map(t => ({ value: t, label: t }))} />
            </Form.Item>
          </Space>
          <Form.Item label="NTP-серверы" name="ntpServers" initialValue="0.pool.ntp.org 1.pool.ntp.org" tooltip="Через пробел">
            <Input placeholder="0.pool.ntp.org 1.pool.ntp.org" />
          </Form.Item>
          <Form.Item label="Banner (текст при входе)" name="bannerText" tooltip="Записывается в /etc/issue.net">
            <Input.TextArea rows={2} placeholder="Authorized access only." />
          </Form.Item>
        </>
      ),
    },
    {
      key: 'network', label: 'Сеть',
      children: (
        <>
          <Space wrap>
            <Form.Item label="DNS-серверы" name="dnsServers" initialValue="8.8.8.8 8.8.4.4" style={{ minWidth: 240 }}>
              <Input placeholder="8.8.8.8 8.8.4.4" />
            </Form.Item>
            <Form.Item label="DNS-домен" name="dnsDomain" style={{ minWidth: 200 }}>
              <Input placeholder="corp.local" />
            </Form.Item>
          </Space>

          <Divider orientationMargin={0}>Сетевые интерфейсы</Divider>
          <Button icon={<PlusOutlined />} type="dashed" style={{ marginBottom: 10 }}
            onClick={() => setIfaces(p => [...p, { _key: uid(), name: 'eth0', address: '', prefix: '24', gateway: '' }])}>
            Добавить интерфейс
          </Button>
          {ifaces.map(iface => (
            <Space key={iface._key} wrap style={{ display: 'flex', marginBottom: 8 }}>
              <Input value={iface.name} placeholder="eth0" style={{ width: 90 }}
                onChange={e => setIfaces(p => p.map(r => r._key === iface._key ? { ...r, name: e.target.value } : r))} />
              <Input value={iface.address} placeholder="192.168.1.10" style={{ width: 150 }}
                onChange={e => setIfaces(p => p.map(r => r._key === iface._key ? { ...r, address: e.target.value } : r))} />
              <Input value={iface.prefix} placeholder="24" style={{ width: 60 }}
                addonBefore="/" onChange={e => setIfaces(p => p.map(r => r._key === iface._key ? { ...r, prefix: e.target.value } : r))} />
              <Input value={iface.gateway} placeholder="Шлюз" style={{ width: 150 }}
                onChange={e => setIfaces(p => p.map(r => r._key === iface._key ? { ...r, gateway: e.target.value } : r))} />
              <Button size="small" danger type="text" icon={<DeleteOutlined />}
                onClick={() => setIfaces(p => p.filter(r => r._key !== iface._key))} />
            </Space>
          ))}

          <Divider orientationMargin={0}>IP Forwarding</Divider>
          <Form.Item label="Включить IP forwarding" name="enableIpForward" valuePropName="checked" initialValue={false}>
            <Switch />
          </Form.Item>
        </>
      ),
    },
    {
      key: 'security', label: 'Безопасность',
      children: (
        <>
          <Divider orientationMargin={0}>SSH</Divider>
          <Space wrap>
            <Form.Item label="SSH порт" name="sshPort" initialValue={22} style={{ width: 130 }}>
              <InputNumber min={1} max={65535} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="Макс. сессий" name="sshMaxSessions" initialValue={10} style={{ width: 130 }}>
              <InputNumber min={1} max={100} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Space wrap>
            <Form.Item label="Пароль аутентификации SSH" name="sshPasswordAuth" valuePropName="checked" initialValue={true}>
              <Switch />
            </Form.Item>
            <Form.Item label="Root-вход по SSH" name="sshRootLogin" valuePropName="checked" initialValue={false}>
              <Switch />
            </Form.Item>
          </Space>
          <Form.Item label="AllowUsers (через пробел)" name="sshAllowUsers" tooltip="Только указанные пользователи смогут подключаться по SSH">
            <Input placeholder="admin deploy" />
          </Form.Item>
          <Form.Item label="Добавить SSH Public Key" name="sshAuthorizedKey">
            <Input.TextArea rows={2} placeholder="ssh-rsa AAAA..." />
          </Form.Item>

          <Divider orientationMargin={0}>Firewall</Divider>
          <Form.Item label="Включить firewall" name="enableFirewall" valuePropName="checked" initialValue={false}>
            <Switch />
          </Form.Item>
          <Button icon={<PlusOutlined />} type="dashed" size="small" style={{ marginBottom: 8 }}
            onClick={() => setFwPorts(p => [...p, { _key: uid(), port: 80, proto: 'tcp' }])}>
            Открыть порт
          </Button>
          {fwPorts.map(fp => (
            <Space key={fp._key} style={{ display: 'flex', marginBottom: 6 }}>
              <InputNumber min={1} max={65535} value={fp.port} style={{ width: 100 }}
                onChange={v => setFwPorts(p => p.map(r => r._key === fp._key ? { ...r, port: v ?? 80 } : r))} />
              <Select value={fp.proto} style={{ width: 80 }}
                options={['tcp','udp'].map(v => ({ value: v, label: v.toUpperCase() }))}
                onChange={v => setFwPorts(p => p.map(r => r._key === fp._key ? { ...r, proto: v } : r))} />
              <Button size="small" danger type="text" icon={<DeleteOutlined />}
                onClick={() => setFwPorts(p => p.filter(r => r._key !== fp._key))} />
            </Space>
          ))}
          <Form.Item label="Дополнительные правила firewall" name="firewallRules" tooltip="Одно правило в строку (firewall-cmd формат)">
            <Input.TextArea rows={2} placeholder="--add-service=http" />
          </Form.Item>

          <Divider orientationMargin={0}>Hardening</Divider>
          <Space wrap>
            <Form.Item label="SELinux режим" name="selinuxMode" initialValue="enforcing" style={{ width: 200 }}>
              <Select options={[{ value: 'enforcing', label: 'Enforcing' }, { value: 'permissive', label: 'Permissive' }, { value: 'disabled', label: 'Disabled' }]} />
            </Form.Item>
            <Form.Item label="Макс. попыток входа" name="maxAuthRetries" initialValue={3} style={{ width: 180 }}>
              <InputNumber min={1} max={20} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Space wrap>
            <Form.Item label="auditd" name="auditdEnabled" valuePropName="checked" initialValue={true}>
              <Switch checkedChildren="вкл" unCheckedChildren="выкл" />
            </Form.Item>
            <Form.Item label="fail2ban" name="fail2banEnabled" valuePropName="checked" initialValue={false}>
              <Switch checkedChildren="вкл" unCheckedChildren="выкл" />
            </Form.Item>
          </Space>
        </>
      ),
    },
    {
      key: 'packages', label: 'Пакеты и Sysctl',
      children: (
        <>
          <Form.Item label="Установить пакеты" name="installPackages" tooltip="Через пробел или запятую">
            <Input.TextArea rows={2} placeholder="vim curl wget htop" />
          </Form.Item>
          <Form.Item label="Удалить пакеты" name="removePackages">
            <Input.TextArea rows={2} placeholder="telnet ftp" />
          </Form.Item>

          <Divider orientationMargin={0}>
            Sysctl параметры{' '}
            <Tooltip title="Записываются в /etc/sysctl.conf при saveToMemory=true. Применяются немедленно через sysctl -w.">
              <InfoCircleOutlined style={{ color: '#1677ff' }} />
            </Tooltip>
          </Divider>
          <Button icon={<PlusOutlined />} type="dashed" size="small" style={{ marginBottom: 8 }}
            onClick={() => setSysctls(p => [...p, { _key: uid(), key: '', value: '' }])}>
            Добавить параметр
          </Button>
          {sysctls.map(sc => (
            <Space key={sc._key} style={{ display: 'flex', marginBottom: 6 }}>
              <Input value={sc.key} placeholder="net.core.somaxconn" style={{ width: 240 }}
                onChange={e => setSysctls(p => p.map(r => r._key === sc._key ? { ...r, key: e.target.value } : r))} />
              <Input value={sc.value} placeholder="1024" style={{ width: 120 }}
                onChange={e => setSysctls(p => p.map(r => r._key === sc._key ? { ...r, value: e.target.value } : r))} />
              <Button size="small" danger type="text" icon={<DeleteOutlined />}
                onClick={() => setSysctls(p => p.filter(r => r._key !== sc._key))} />
            </Space>
          ))}
        </>
      ),
    },
    {
      key: 'users', label: 'Пользователи',
      children: (
        <>
          <Button icon={<PlusOutlined />} type="dashed" style={{ marginBottom: 10 }}
            onClick={() => setUsers(p => [...p, { _key: uid(), name: '', password: '', sudo: false, locked: false, create: true }])}>
            Добавить пользователя
          </Button>
          {users.map(u => (
            <Space key={u._key} wrap style={{ display: 'flex', marginBottom: 8, padding: '8px', border: '1px solid #f0f0f0', borderRadius: 6 }}>
              <Input value={u.name} placeholder="username" style={{ width: 140 }}
                onChange={e => setUsers(p => p.map(r => r._key === u._key ? { ...r, name: e.target.value } : r))} />
              <Input.Password value={u.password} placeholder="Пароль" style={{ width: 140 }}
                onChange={e => setUsers(p => p.map(r => r._key === u._key ? { ...r, password: e.target.value } : r))} />
              <Tooltip title="sudo/wheel группа">
                <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                  <Switch size="small" checked={u.sudo} onChange={v => setUsers(p => p.map(r => r._key === u._key ? { ...r, sudo: v } : r))} />
                  <Text style={{ fontSize: 12 }}>sudo</Text>
                </div>
              </Tooltip>
              <Tooltip title="Заблокировать учётную запись">
                <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                  <Switch size="small" checked={u.locked} onChange={v => setUsers(p => p.map(r => r._key === u._key ? { ...r, locked: v } : r))} />
                  <Text style={{ fontSize: 12 }}>lock</Text>
                </div>
              </Tooltip>
              <Button size="small" danger type="text" icon={<DeleteOutlined />}
                onClick={() => setUsers(p => p.filter(r => r._key !== u._key))} />
            </Space>
          ))}
        </>
      ),
    },
    {
      key: 'services', label: 'Службы',
      children: (
        <>
          <Button icon={<PlusOutlined />} type="dashed" style={{ marginBottom: 10 }}
            onClick={() => setServices(p => [...p, { _key: uid(), name: '', enabled: true, running: true }])}>
            Добавить службу
          </Button>
          {services.map(svc => (
            <Space key={svc._key} style={{ display: 'flex', marginBottom: 8 }}>
              <Input value={svc.name} placeholder="nginx" style={{ width: 180 }}
                onChange={e => setServices(p => p.map(r => r._key === svc._key ? { ...r, name: e.target.value } : r))} />
              <Tooltip title="systemctl enable/disable">
                <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                  <Switch size="small" checked={svc.enabled} onChange={v => setServices(p => p.map(r => r._key === svc._key ? { ...r, enabled: v } : r))} />
                  <Text style={{ fontSize: 12 }}>enabled</Text>
                </div>
              </Tooltip>
              <Tooltip title="systemctl start/stop">
                <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                  <Switch size="small" checked={svc.running} onChange={v => setServices(p => p.map(r => r._key === svc._key ? { ...r, running: v } : r))} />
                  <Text style={{ fontSize: 12 }}>running</Text>
                </div>
              </Tooltip>
              <Button size="small" danger type="text" icon={<DeleteOutlined />}
                onClick={() => setServices(p => p.filter(r => r._key !== svc._key))} />
            </Space>
          ))}

          <Divider orientationMargin={0}>Cron</Divider>
          <Button icon={<PlusOutlined />} type="dashed" size="small" style={{ marginBottom: 8 }}
            onClick={() => setCrons(p => [...p, { _key: uid(), schedule: '0 3 * * *', command: '', user: 'root' }])}>
            Добавить cron
          </Button>
          {crons.map(cr => (
            <Space key={cr._key} style={{ display: 'flex', marginBottom: 6 }}>
              <Input value={cr.schedule} placeholder="0 3 * * *" style={{ width: 130 }}
                onChange={e => setCrons(p => p.map(r => r._key === cr._key ? { ...r, schedule: e.target.value } : r))} />
              <Input value={cr.command} placeholder="команда" style={{ width: 200 }}
                onChange={e => setCrons(p => p.map(r => r._key === cr._key ? { ...r, command: e.target.value } : r))} />
              <Input value={cr.user} placeholder="root" style={{ width: 80 }}
                onChange={e => setCrons(p => p.map(r => r._key === cr._key ? { ...r, user: e.target.value } : r))} />
              <Button size="small" danger type="text" icon={<DeleteOutlined />}
                onClick={() => setCrons(p => p.filter(r => r._key !== cr._key))} />
            </Space>
          ))}
        </>
      ),
    },
    {
      key: 'monitoring', label: 'Мониторинг',
      children: (
        <>
          <Form.Item label="Установить Node Exporter (Prometheus)" name="installNodeExporter" valuePropName="checked" initialValue={false}>
            <Switch />
          </Form.Item>
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
        </>
      ),
    },
    {
      key: 'connection', label: 'Подключение',
      children: (
        <>
          <Space wrap>
            <Form.Item label="SSH-логин" name="sshUsername" rules={[{ required: true, message: 'Введите логин' }]} style={{ minWidth: 180 }}>
              <Input placeholder="root" />
            </Form.Item>
            <Form.Item label="SSH-пароль" name="sshPassword" rules={[{ required: true, message: 'Введите пароль' }]} style={{ minWidth: 200 }}>
              <Input.Password />
            </Form.Item>
            <Form.Item label="Порт" name="sshPort" initialValue={22} style={{ width: 110 }}>
              <InputNumber min={1} max={65535} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
        </>
      ),
    },
  ];

  return (
    <Modal
      title={`Linux — ${hostname || 'устройство'}`}
      open={open} onCancel={onClose} footer={null} width={800} destroyOnClose
    >
      <Form form={form} layout="vertical" autoComplete="off">
        <ConfigPrefillBar open={open} deviceId={deviceId} deviceHostname={hostname} onPrefill={prefill} />
        <Tabs type="card" items={tabItems} />

        <div style={{ marginTop: 16 }}>
          <SaveToMemoryToggle
            value={saveToMemory}
            onChange={setSaveToMemory}
            onLabel="Сохранить в конфиг-файлы (persist)"
            offLabel="Только в текущий сеанс (no persist)"
            onDescription="Изменения записываются в /etc/hostname, /etc/resolv.conf, sysctl.conf и т.д. Выживают перезагрузку."
            offDescription="Команды применяются без записи в файлы. Перезагрузка вернёт старые настройки — безопасно для тестирования."
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
