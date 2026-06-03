import React, { useState } from 'react';
import {
  Modal, Form, Input, InputNumber, Select, Switch, Button, Tabs,
  Divider, Space, Typography, Radio, message,
} from 'antd';
import { PlusOutlined, DeleteOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { configApi } from '../api/configApi';
import SaveToMemoryToggle from './SaveToMemoryToggle';

const { Text } = Typography;

interface StorageRow { _key: string; name: string; type: string; path: string; server: string; export: string; content: string[]; pruneKeepLast: number }
interface PveUser    { _key: string; userid: string; email: string; role: string; group: string; password: string; enabled: boolean }
interface BridgeRow  { _key: string; name: string; ports: string; address: string; gateway: string }

function uid() { return Math.random().toString(36).slice(2, 8); }

interface Props { open: boolean; onClose: () => void; hostname?: string; deviceId?: number }

export default function ConfigProxmoxModal({ open, onClose, hostname, deviceId }: Props) {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [saveToMemory, setSaveToMemory] = useState(true);
  const [storages, setStorages] = useState<StorageRow[]>([]);
  const [pveUsers, setPveUsers] = useState<PveUser[]>([]);
  const [bridges,  setBridges]  = useState<BridgeRow[]>([]);

  const vmType = Form.useWatch('vmType', form);

  const handleSubmit = async () => {
    if (!deviceId) return message.error('Устройство не выбрано');
    let fields: Record<string, any>;
    try { fields = await form.validateFields(); } catch { return; }

    const { sshUsername, sshPassword, sshPort, ...configFields } = fields as Record<string, any>;
    const configData = {
      ...configFields,
      storages:  storages.map(({ _key, ...r }) => r),
      pveUsers:  pveUsers.map(({ _key, ...r }) => r),
      bridges:   bridges.map(({ _key, ...r }) => r),
      saveToMemory,
    };

    setSubmitting(true);
    try {
      await configApi.applyConfig({
        deviceIds: [deviceId],
        configData,
        credentials: { [deviceId]: { username: sshUsername, password: sshPassword, port: sshPort ?? 22 } },
      });
      message.success('Proxmox конфигурация применена');
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
          <Space wrap>
            <Form.Item label="Hostname узла" name="hostname" style={{ minWidth: 220 }}>
              <Input placeholder="pve1.local" />
            </Form.Item>
            <Form.Item label="Часовой пояс" name="timezone" initialValue="Europe/Moscow" style={{ minWidth: 200 }}>
              <Select showSearch options={['UTC','Europe/Moscow','Europe/Berlin','Europe/London'].map(t => ({ value: t, label: t }))} />
            </Form.Item>
          </Space>
          <Space wrap>
            <Form.Item label="NTP-сервер" name="ntpServer" style={{ minWidth: 220 }}>
              <Input placeholder="192.168.1.1" />
            </Form.Item>
            <Form.Item label="DNS серверы" name="dnsServers" style={{ minWidth: 220 }}>
              <Input placeholder="8.8.8.8 8.8.4.4" />
            </Form.Item>
          </Space>
          <Form.Item label="Email для уведомлений" name="emailTo">
            <Input placeholder="admin@example.com" style={{ maxWidth: 300 }} />
          </Form.Item>

          <Divider orientationMargin={0}>Datacenter</Divider>
          <Space wrap>
            <Form.Item label="Язык интерфейса" name="language" initialValue="en" style={{ width: 160 }}>
              <Select options={[{ value: 'en', label: 'English' }, { value: 'ru', label: 'Русский' }]} />
            </Form.Item>
            <Form.Item label="Раскладка" name="keyboard" initialValue="en-us" style={{ width: 140 }}>
              <Select options={[{ value: 'en-us', label: 'US' }, { value: 'ru', label: 'RU' }]} />
            </Form.Item>
            <Form.Item label="Консоль VM" name="consoleViewer" initialValue="html5" style={{ width: 180 }}>
              <Select options={[{ value: 'html5', label: 'HTML5 (noVNC)' }, { value: 'vv', label: 'SPICE' }, { value: 'xtermjs', label: 'xterm.js' }]} />
            </Form.Item>
            <Form.Item label="Max workers" name="maxWorkers" initialValue={4} style={{ width: 130 }}>
              <InputNumber min={1} max={32} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.Item label="HTTP Proxy" name="httpProxy">
            <Input placeholder="http://proxy.local:3128" style={{ maxWidth: 300 }} />
          </Form.Item>
        </>
      ),
    },
    {
      key: 'network', label: 'Сеть',
      children: (
        <>
          <Button icon={<PlusOutlined />} type="dashed" style={{ marginBottom: 10 }}
            onClick={() => setBridges(p => [...p, { _key: uid(), name: 'vmbr1', ports: '', address: '', gateway: '' }])}>
            Добавить мост (bridge)
          </Button>
          {bridges.map(br => (
            <Space key={br._key} wrap style={{ display: 'flex', marginBottom: 8, padding: '8px', border: '1px solid #f0f0f0', borderRadius: 6 }}>
              <div>
                <Text type="secondary" style={{ fontSize: 11 }}>Имя</Text>
                <Input value={br.name} placeholder="vmbr1" style={{ width: 100, display: 'block' }}
                  onChange={e => setBridges(p => p.map(r => r._key === br._key ? { ...r, name: e.target.value } : r))} />
              </div>
              <div>
                <Text type="secondary" style={{ fontSize: 11 }}>Физ. порты</Text>
                <Input value={br.ports} placeholder="eth1" style={{ width: 120, display: 'block' }}
                  onChange={e => setBridges(p => p.map(r => r._key === br._key ? { ...r, ports: e.target.value } : r))} />
              </div>
              <div>
                <Text type="secondary" style={{ fontSize: 11 }}>IP/Prefix (CIDR)</Text>
                <Input value={br.address} placeholder="10.0.0.1/24" style={{ width: 150, display: 'block' }}
                  onChange={e => setBridges(p => p.map(r => r._key === br._key ? { ...r, address: e.target.value } : r))} />
              </div>
              <div>
                <Text type="secondary" style={{ fontSize: 11 }}>Шлюз</Text>
                <Input value={br.gateway} placeholder="10.0.0.254" style={{ width: 140, display: 'block' }}
                  onChange={e => setBridges(p => p.map(r => r._key === br._key ? { ...r, gateway: e.target.value } : r))} />
              </div>
              <Button size="small" danger type="text" icon={<DeleteOutlined />} style={{ alignSelf: 'flex-end' }}
                onClick={() => setBridges(p => p.filter(r => r._key !== br._key))} />
            </Space>
          ))}
        </>
      ),
    },
    {
      key: 'storage', label: 'Хранилище',
      children: (
        <>
          <Button icon={<PlusOutlined />} type="dashed" style={{ marginBottom: 10 }}
            onClick={() => setStorages(p => [...p, { _key: uid(), name: '', type: 'dir', path: '/var/lib/vz/new', server: '', export: '', content: ['images','iso'], pruneKeepLast: 3 }])}>
            Добавить хранилище
          </Button>
          {storages.map(st => (
            <Space key={st._key} wrap style={{ display: 'flex', marginBottom: 8, padding: '10px', border: '1px solid #f0f0f0', borderRadius: 6 }}>
              <div>
                <Text type="secondary" style={{ fontSize: 11 }}>Имя</Text>
                <Input value={st.name} placeholder="local-new" style={{ width: 130, display: 'block' }}
                  onChange={e => setStorages(p => p.map(r => r._key === st._key ? { ...r, name: e.target.value } : r))} />
              </div>
              <div>
                <Text type="secondary" style={{ fontSize: 11 }}>Тип</Text>
                <Select value={st.type} style={{ width: 110, display: 'block' }}
                  options={['dir','nfs','cifs','lvmthin','zfspool','rbd'].map(v => ({ value: v, label: v }))}
                  onChange={v => setStorages(p => p.map(r => r._key === st._key ? { ...r, type: v } : r))} />
              </div>
              {st.type === 'dir' && (
                <div>
                  <Text type="secondary" style={{ fontSize: 11 }}>Путь</Text>
                  <Input value={st.path} style={{ width: 180, display: 'block' }}
                    onChange={e => setStorages(p => p.map(r => r._key === st._key ? { ...r, path: e.target.value } : r))} />
                </div>
              )}
              {(st.type === 'nfs' || st.type === 'cifs') && (
                <>
                  <div>
                    <Text type="secondary" style={{ fontSize: 11 }}>Сервер</Text>
                    <Input value={st.server} style={{ width: 150, display: 'block' }}
                      onChange={e => setStorages(p => p.map(r => r._key === st._key ? { ...r, server: e.target.value } : r))} />
                  </div>
                  <div>
                    <Text type="secondary" style={{ fontSize: 11 }}>Путь/Шара</Text>
                    <Input value={st.export} style={{ width: 180, display: 'block' }}
                      onChange={e => setStorages(p => p.map(r => r._key === st._key ? { ...r, export: e.target.value } : r))} />
                  </div>
                </>
              )}
              <div>
                <Text type="secondary" style={{ fontSize: 11 }}>Контент</Text>
                <Select mode="multiple" value={st.content} style={{ width: 220, display: 'block' }}
                  options={['images','iso','backup','vztmpl','snippets'].map(v => ({ value: v, label: v }))}
                  onChange={v => setStorages(p => p.map(r => r._key === st._key ? { ...r, content: v } : r))} />
              </div>
              <div>
                <Text type="secondary" style={{ fontSize: 11 }}>Keep-last</Text>
                <InputNumber value={st.pruneKeepLast} min={1} max={365} style={{ width: 80, display: 'block' }}
                  onChange={v => setStorages(p => p.map(r => r._key === st._key ? { ...r, pruneKeepLast: v ?? 3 } : r))} />
              </div>
              <Button size="small" danger type="text" icon={<DeleteOutlined />} style={{ alignSelf: 'flex-end' }}
                onClick={() => setStorages(p => p.filter(r => r._key !== st._key))} />
            </Space>
          ))}

          <Divider orientationMargin={0}>Резервное копирование</Divider>
          <Space wrap>
            <Form.Item label="Хранилище для бэкапов" name="backupStorage" style={{ minWidth: 200 }}>
              <Input placeholder="local-backup" />
            </Form.Item>
            <Form.Item label="Расписание (cron)" name="backupSchedule" style={{ minWidth: 200 }}>
              <Input placeholder="0 3 * * *" />
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
            onClick={() => setPveUsers(p => [...p, { _key: uid(), userid: '', email: '', role: 'PVEAdmin', group: '', password: '', enabled: true }])}>
            Добавить пользователя
          </Button>
          {pveUsers.map(u => (
            <Space key={u._key} wrap style={{ display: 'flex', marginBottom: 8, padding: '10px', border: '1px solid #f0f0f0', borderRadius: 6 }}>
              <div>
                <Text type="secondary" style={{ fontSize: 11 }}>UserID@realm</Text>
                <Input value={u.userid} placeholder="john@pve" style={{ width: 160, display: 'block' }}
                  onChange={e => setPveUsers(p => p.map(r => r._key === u._key ? { ...r, userid: e.target.value } : r))} />
              </div>
              <div>
                <Text type="secondary" style={{ fontSize: 11 }}>Пароль</Text>
                <Input.Password value={u.password} style={{ width: 140, display: 'block' }}
                  onChange={e => setPveUsers(p => p.map(r => r._key === u._key ? { ...r, password: e.target.value } : r))} />
              </div>
              <div>
                <Text type="secondary" style={{ fontSize: 11 }}>Email</Text>
                <Input value={u.email} placeholder="john@corp.com" style={{ width: 180, display: 'block' }}
                  onChange={e => setPveUsers(p => p.map(r => r._key === u._key ? { ...r, email: e.target.value } : r))} />
              </div>
              <div>
                <Text type="secondary" style={{ fontSize: 11 }}>Роль</Text>
                <Select value={u.role} style={{ width: 150, display: 'block' }}
                  options={['PVEAdmin','PVEVMUser','PVEAuditor','PVEDatastoreAdmin','Administrator'].map(v => ({ value: v, label: v }))}
                  onChange={v => setPveUsers(p => p.map(r => r._key === u._key ? { ...r, role: v } : r))} />
              </div>
              <div>
                <Text type="secondary" style={{ fontSize: 11 }}>Включён</Text>
                <Switch checked={u.enabled} onChange={v => setPveUsers(p => p.map(r => r._key === u._key ? { ...r, enabled: v } : r))} />
              </div>
              <Button size="small" danger type="text" icon={<DeleteOutlined />} style={{ alignSelf: 'flex-end' }}
                onClick={() => setPveUsers(p => p.filter(r => r._key !== u._key))} />
            </Space>
          ))}
        </>
      ),
    },
    {
      key: 'virtualization', label: 'Виртуализация',
      children: (
        <>
          <Form.Item label="Создать гостевую систему" name="vmType" initialValue="">
            <Radio.Group>
              <Radio.Button value="">Нет</Radio.Button>
              <Radio.Button value="qemu">QEMU VM</Radio.Button>
              <Radio.Button value="lxc">LXC Container</Radio.Button>
            </Radio.Group>
          </Form.Item>

          {vmType === 'qemu' && (
            <>
              <Space wrap>
                <Form.Item label="VM ID" name="vmId" initialValue={100} style={{ width: 120 }}>
                  <InputNumber min={100} style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item label="Имя" name="vmName" style={{ minWidth: 180 }}>
                  <Input placeholder="my-vm" />
                </Form.Item>
                <Form.Item label="Узел" name="vmNode" style={{ width: 120 }}>
                  <Input placeholder="pve" />
                </Form.Item>
                <Form.Item label="BIOS" name="vmBios" initialValue="seabios" style={{ width: 150 }}>
                  <Select options={[{ value: 'seabios', label: 'SeaBIOS' }, { value: 'ovmf', label: 'OVMF (UEFI)' }]} />
                </Form.Item>
              </Space>
              <Space wrap>
                <Form.Item label="RAM (MB)" name="vmMemory" initialValue={2048} style={{ width: 130 }}>
                  <InputNumber min={512} step={512} style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item label="CPU ядра" name="vmCores" initialValue={2} style={{ width: 120 }}>
                  <InputNumber min={1} max={64} style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item label="Сокеты" name="vmSockets" initialValue={1} style={{ width: 110 }}>
                  <InputNumber min={1} max={4} style={{ width: '100%' }} />
                </Form.Item>
              </Space>
              <Space wrap>
                <Form.Item label="Диск (GB)" name="vmDiskSize" initialValue={32} style={{ width: 120 }}>
                  <InputNumber min={1} style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item label="Хранилище диска" name="vmDiskStorage" initialValue="local-lvm" style={{ minWidth: 160 }}>
                  <Input />
                </Form.Item>
                <Form.Item label="Мост" name="vmNetBridge" initialValue="vmbr0" style={{ width: 130 }}>
                  <Select options={['vmbr0','vmbr1','vmbr2'].map(v => ({ value: v, label: v }))} />
                </Form.Item>
                <Form.Item label="Сеть модель" name="vmNetModel" initialValue="virtio" style={{ width: 180 }}>
                  <Select options={[{ value: 'virtio', label: 'VirtIO (рекомендуется)' }, { value: 'e1000', label: 'Intel E1000' }]} />
                </Form.Item>
              </Space>
            </>
          )}

          {vmType === 'lxc' && (
            <>
              <Space wrap>
                <Form.Item label="CT ID" name="ctId" initialValue={200} style={{ width: 120 }}>
                  <InputNumber min={100} style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item label="Hostname CT" name="ctName" style={{ minWidth: 180 }}>
                  <Input placeholder="my-container" />
                </Form.Item>
              </Space>
              <Form.Item label="Шаблон ОС" name="ctTemplate" style={{ maxWidth: 340 }}>
                <Select options={[
                  { value: 'debian-12-standard_12.2-1_amd64.tar.zst', label: 'Debian 12' },
                  { value: 'ubuntu-22.04-standard_22.04-1_amd64.tar.zst', label: 'Ubuntu 22.04' },
                  { value: 'alpine-3.18-default_20231026_amd64.tar.xz', label: 'Alpine 3.18' },
                ]} />
              </Form.Item>
              <Space wrap>
                <Form.Item label="RAM (MB)" name="ctMemory" initialValue={1024} style={{ width: 130 }}>
                  <InputNumber min={128} step={256} style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item label="Swap" name="ctSwap" initialValue={512} style={{ width: 120 }}>
                  <InputNumber min={0} style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item label="CPU" name="ctCores" initialValue={1} style={{ width: 110 }}>
                  <InputNumber min={1} max={16} style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item label="Диск (GB)" name="ctDiskSize" initialValue={8} style={{ width: 120 }}>
                  <InputNumber min={1} style={{ width: '100%' }} />
                </Form.Item>
              </Space>
              <Form.Item label="Пароль root" name="ctPassword">
                <Input.Password style={{ maxWidth: 260 }} />
              </Form.Item>
              <Form.Item label="Непривилегированный" name="ctUnprivileged" valuePropName="checked" initialValue={true}>
                <Switch />
              </Form.Item>
            </>
          )}
        </>
      ),
    },
    {
      key: 'connection', label: 'Подключение',
      children: (
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
      ),
    },
  ];

  return (
    <Modal
      title={`Proxmox VE — ${hostname || 'узел'}`}
      open={open} onCancel={onClose} footer={null} width={880} destroyOnClose
    >
      <Form form={form} layout="vertical" autoComplete="off">
        <Tabs type="card" items={tabItems} />

        <div style={{ marginTop: 16 }}>
          <SaveToMemoryToggle
            value={saveToMemory}
            onChange={setSaveToMemory}
            onLabel="Сохранить (pvesh + config files)"
            offLabel="Применить без записи в конфиг"
            onDescription="Изменения сохраняются через pvesh API и записываются в конфигурационные файлы Proxmox."
            offDescription="Только транзиентные изменения. Перезагрузка узла Proxmox отменит их."
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
