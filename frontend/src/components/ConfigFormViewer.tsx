import React, { useMemo, useState } from 'react';
import {
  Button, Input, InputNumber, Switch, Select, Tag, Tooltip, Typography,
  Space, Empty, Modal, Form, message,
} from 'antd';
import {
  LockOutlined, EditOutlined, UndoOutlined, SendOutlined,
} from '@ant-design/icons';
import { configApi } from '../api/configApi';

const { Text } = Typography;

// ─────────────────────────────────────────────────────────────────────────────
// Описание редактируемых полей по типам устройств
// ─────────────────────────────────────────────────────────────────────────────

type Widget =
  | { kind: 'text' }
  | { kind: 'number'; min?: number; max?: number; unit?: string }
  | { kind: 'boolean' }
  | { kind: 'select'; options: { value: any; label: string }[] };

interface FieldMeta {
  label: string;
  widget: Widget;
  help?: string;
}

/**
 * Реестр удалённо редактируемых параметров. Ключ — либо полный путь
 * (с точкой, напр. "domain.name"), либо имя листа (напр. "hostname").
 * Поля, отсутствующие в реестре, показываются только для чтения, так как
 * их нельзя применить к устройству удалённо (инвентарные/системные данные).
 */
const REGISTRY: Record<string, Record<string, FieldMeta>> = {
  // ── МФУ (SNMP SET) ────────────────────────────────────────────────────────
  МФУ: {
    deviceName: { label: 'Имя устройства (sysName)', widget: { kind: 'text' } },
    location:   { label: 'Расположение (sysLocation)', widget: { kind: 'text' } },
    contact:    { label: 'Контакт (sysContact)', widget: { kind: 'text' } },
    brand: {
      label: 'Бренд', widget: {
        kind: 'select', options: [
          'kyocera', 'canon', 'ricoh', 'hp', 'xerox', 'samsung', 'brother', 'epson',
        ].map(v => ({ value: v, label: v.charAt(0).toUpperCase() + v.slice(1) })),
      },
    },
    tonerSave: { label: 'Экономия тонера', widget: { kind: 'boolean' } },
    resolution: {
      label: 'Разрешение', widget: {
        kind: 'select', options: [
          { value: '300dpi', label: '300 dpi' },
          { value: '600dpi', label: '600 dpi' },
          { value: '1200dpi', label: '1200 dpi' },
        ],
      },
    },
    duplex: {
      label: 'Двусторонняя печать', widget: {
        kind: 'select', options: [
          { value: 'simplex',    label: 'Односторонняя' },
          { value: 'long-edge',  label: 'По длинной стороне' },
          { value: 'short-edge', label: 'По короткой стороне' },
        ],
      },
    },
    powerSaveMinutes: { label: 'Спящий режим', widget: { kind: 'number', min: 0, max: 240, unit: 'мин' } },
    copies:           { label: 'Копий по умолчанию', widget: { kind: 'number', min: 1, max: 999 } },
  },

  // ── Cisco (SSH) ───────────────────────────────────────────────────────────
  CISCO: {
    hostname:   { label: 'Hostname', widget: { kind: 'text' } },
    community:  { label: 'SNMP community', widget: { kind: 'text' } },
    ipAddress:  { label: 'IP-адрес', widget: { kind: 'text' } },
    subnetMask: { label: 'Маска подсети', widget: { kind: 'text' } },
    shutdown:   { label: 'Интерфейс отключён', widget: { kind: 'boolean' } },
  },

  // ── ПК / Windows (WinRM) ──────────────────────────────────────────────────
  ПК: {
    hostname:           { label: 'Имя компьютера', widget: { kind: 'text' } },
    ipAddress:          { label: 'IP-адрес', widget: { kind: 'text' } },
    gateway:            { label: 'Шлюз', widget: { kind: 'text' } },
    'domain.name':      { label: 'Домен', widget: { kind: 'text' } },
  },

  // ── VM / Proxmox (SSH) ────────────────────────────────────────────────────
  VM: {
    hostname: { label: 'Hostname', widget: { kind: 'text' } },
  },
};

// Типы устройств, для применения к которым нужны учётные данные (SSH/WinRM).
// МФУ настраивается через SNMP SET с management-сервера — креды не нужны.
const NEEDS_CREDENTIALS = (deviceType: string) => deviceType !== 'МФУ';

// ─────────────────────────────────────────────────────────────────────────────
// Утилиты плоского представления конфигурации
// ─────────────────────────────────────────────────────────────────────────────

interface Leaf { path: string; value: any; }

function flattenTyped(obj: any, prefix = ''): Leaf[] {
  if (obj === null || obj === undefined) return prefix ? [{ path: prefix, value: null }] : [];
  if (Array.isArray(obj))               return [{ path: prefix, value: obj }];
  if (typeof obj !== 'object')          return [{ path: prefix, value: obj }];

  const out: Leaf[] = [];
  for (const [k, v] of Object.entries(obj)) {
    const p = prefix ? `${prefix}.${k}` : k;
    if (v !== null && typeof v === 'object' && !Array.isArray(v)) out.push(...flattenTyped(v, p));
    else out.push({ path: p, value: v });
  }
  return out;
}

function deepClone<T>(obj: T): T {
  return typeof structuredClone === 'function'
    ? structuredClone(obj)
    : JSON.parse(JSON.stringify(obj));
}

function deepSet(obj: any, path: string, value: any) {
  const parts = path.split('.');
  let cur = obj;
  for (let i = 0; i < parts.length - 1; i++) {
    if (typeof cur[parts[i]] !== 'object' || cur[parts[i]] === null) cur[parts[i]] = {};
    cur = cur[parts[i]];
  }
  cur[parts[parts.length - 1]] = value;
}

function applyEdits(original: Record<string, any>, edits: Record<string, any>) {
  const clone = deepClone(original);
  for (const [path, val] of Object.entries(edits)) deepSet(clone, path, val);
  return clone;
}

function lookupMeta(deviceType: string, path: string): FieldMeta | null {
  const reg = REGISTRY[deviceType] || {};
  if (reg[path]) return reg[path];
  const leaf = path.split('.').pop()!;
  return reg[leaf] ?? null;
}

function isPrimitive(v: any): boolean {
  return v === null || (typeof v !== 'object');
}

function readonlyDisplay(value: any): string {
  if (value === null || value === undefined) return '—';
  if (Array.isArray(value) || typeof value === 'object') return JSON.stringify(value);
  if (typeof value === 'boolean') return value ? 'Да' : 'Нет';
  return String(value);
}

// ─────────────────────────────────────────────────────────────────────────────
// Компонент
// ─────────────────────────────────────────────────────────────────────────────

export interface ConfigFormViewerProps {
  config: Record<string, any> | null;
  deviceType: string;            // 'МФУ' | 'CISCO' | 'ПК' | 'VM'
  deviceId: number;
  hostname?: string;
  onApplied?: () => void;        // вызвать после успешной отправки (перезагрузить версии)
}

export default function ConfigFormViewer({
  config, deviceType, deviceId, hostname, onApplied,
}: ConfigFormViewerProps) {

  const [edits, setEdits] = useState<Record<string, any>>({});
  const [submitting, setSubmitting] = useState(false);
  const [credOpen, setCredOpen] = useState(false);
  const [credForm] = Form.useForm();

  const leaves = useMemo(() => flattenTyped(config ?? {}), [config]);

  const rows = useMemo(() => leaves.map(({ path, value }) => {
    const meta = lookupMeta(deviceType, path);
    const editable = meta !== null && isPrimitive(value);
    return { path, value, meta, editable };
  }), [leaves, deviceType]);

  const editableRows = rows.filter(r => r.editable);
  const readonlyRows = rows.filter(r => !r.editable);

  const changedPaths = useMemo(
    () => Object.keys(edits).filter(p => {
      const original = leaves.find(l => l.path === p)?.value;
      return JSON.stringify(edits[p]) !== JSON.stringify(original);
    }),
    [edits, leaves],
  );

  const currentValue = (path: string) => {
    if (path in edits) return edits[path];
    return leaves.find(l => l.path === path)?.value;
  };

  const setValue = (path: string, value: any) =>
    setEdits(prev => ({ ...prev, [path]: value }));

  const revert = (path: string) =>
    setEdits(prev => { const n = { ...prev }; delete n[path]; return n; });

  const resetAll = () => setEdits({});

  // ── Отправка ────────────────────────────────────────────────────────────
  const handleApplyClick = () => {
    if (changedPaths.length === 0) return;
    if (NEEDS_CREDENTIALS(deviceType)) {
      credForm.resetFields();
      setCredOpen(true);
    } else {
      doApply({});
    }
  };

  const doApply = async (credentials: Record<number, any>) => {
    if (!config) return;
    setSubmitting(true);
    try {
      const merged = applyEdits(config, edits);
      const res = await configApi.applyConfig({
        deviceIds: [deviceId],
        configData: merged,
        credentials,
      });
      const outcome = await configApi.resolveApplyOutcome(res.data);
      if (outcome.kind === 'failed') {
        message.error(`Не удалось применить на ${hostname ?? deviceId}: ` + (outcome.error || 'устройство недоступно'));
        return; // не сбрасываем правки — пользователь может повторить
      }
      if (outcome.kind === 'scheduled') {
        message.warning(`Устройство ${hostname ?? deviceId} офлайн — изменения применятся автоматически при появлении в сети`);
      } else if (outcome.kind === 'pending') {
        message.info(`Применение на ${hostname ?? deviceId} выполняется в фоне — проверьте статус позже`);
      } else {
        message.success(`Изменения отправлены на устройство ${hostname ?? deviceId}`);
      }
      setEdits({});
      setCredOpen(false);
      onApplied?.();
    } catch (e: any) {
      message.error(e?.response?.data?.message || 'Ошибка применения конфигурации');
    } finally {
      setSubmitting(false);
    }
  };

  const submitWithCredentials = async () => {
    let vals: any;
    try { vals = await credForm.validateFields(); } catch { return; }
    doApply({
      [deviceId]: { username: vals.username, password: vals.password, port: vals.port },
    });
  };

  // ── Рендер виджета ────────────────────────────────────────────────────────
  const renderWidget = (path: string, meta: FieldMeta) => {
    const val = currentValue(path);
    switch (meta.widget.kind) {
      case 'boolean':
        return <Switch checked={!!val} onChange={v => setValue(path, v)} />;
      case 'number':
        return (
          <InputNumber
            value={val as number}
            min={meta.widget.min}
            max={meta.widget.max}
            addonAfter={meta.widget.unit}
            style={{ width: 160 }}
            onChange={v => setValue(path, v)}
          />
        );
      case 'select':
        return (
          <Select
            value={val}
            style={{ minWidth: 220 }}
            options={meta.widget.options}
            onChange={v => setValue(path, v)}
          />
        );
      case 'text':
      default:
        return (
          <Input
            value={val ?? ''}
            style={{ maxWidth: 320 }}
            onChange={e => setValue(path, e.target.value)}
          />
        );
    }
  };

  if (!config || rows.length === 0) {
    return <Empty description="Нет данных конфигурации" />;
  }

  return (
    <div>
      {/* ── Панель управления ── */}
      <div style={{
        display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 12,
        marginBottom: 16, padding: '10px 14px',
        background: '#f5f7fa', borderRadius: 8, border: '1px solid #e8e8e8',
      }}>
        <Space size={6}>
          <Tag icon={<EditOutlined />} color="blue">{editableRows.length} редактируемых</Tag>
          <Tag icon={<LockOutlined />} color="default">{readonlyRows.length} только чтение</Tag>
          {changedPaths.length > 0 && (
            <Tag color="gold">{changedPaths.length} изменено</Tag>
          )}
        </Space>

        <div style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
          <Button
            size="small"
            icon={<UndoOutlined />}
            disabled={changedPaths.length === 0 || submitting}
            onClick={resetAll}
          >
            Сбросить
          </Button>
          <Button
            type="primary"
            size="small"
            icon={<SendOutlined />}
            disabled={changedPaths.length === 0}
            loading={submitting}
            onClick={handleApplyClick}
          >
            Применить и отправить
          </Button>
        </div>
      </div>

      {/* ── Редактируемые поля ── */}
      {editableRows.length > 0 && (
        <div style={{ marginBottom: 20 }}>
          <Text strong style={{ display: 'block', marginBottom: 8, color: '#1d4ed8' }}>
            <EditOutlined /> Удалённо редактируемые параметры
          </Text>
          <div style={{ border: '1px solid #e8e8e8', borderRadius: 8, overflow: 'hidden' }}>
            {editableRows.map((row, idx) => {
              const changed = changedPaths.includes(row.path);
              return (
                <div key={row.path} style={{
                  display: 'flex', alignItems: 'center', gap: 12,
                  padding: '10px 14px',
                  background: changed ? '#fffbe6' : '#fff',
                  borderBottom: idx < editableRows.length - 1 ? '1px solid #f0f0f0' : 'none',
                }}>
                  <div style={{ width: 240, flexShrink: 0 }}>
                    <Tooltip title={row.path}>
                      <Text strong>{row.meta!.label}</Text>
                    </Tooltip>
                    {row.meta!.help && (
                      <div><Text type="secondary" style={{ fontSize: 12 }}>{row.meta!.help}</Text></div>
                    )}
                  </div>
                  <div style={{ flex: 1 }}>{renderWidget(row.path, row.meta!)}</div>
                  {changed && (
                    <Space size={4} style={{ flexShrink: 0 }}>
                      <Tag color="gold" style={{ margin: 0 }}>изменено</Tag>
                      <Tooltip title="Вернуть исходное значение">
                        <Button size="small" type="text" icon={<UndoOutlined />} onClick={() => revert(row.path)} />
                      </Tooltip>
                    </Space>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* ── Поля только для чтения ── */}
      {readonlyRows.length > 0 && (
        <div>
          <Text strong style={{ display: 'block', marginBottom: 8, color: '#8c8c8c' }}>
            <LockOutlined /> Только для чтения (нельзя изменить удалённо)
          </Text>
          <div style={{ border: '1px solid #f0f0f0', borderRadius: 8, overflow: 'hidden' }}>
            {readonlyRows.map((row, idx) => (
              <div key={row.path} style={{
                display: 'flex', alignItems: 'center', gap: 12,
                padding: '7px 14px',
                background: idx % 2 ? '#fafafa' : '#fff',
                borderBottom: idx < readonlyRows.length - 1 ? '1px solid #f5f5f5' : 'none',
              }}>
                <div style={{ width: 240, flexShrink: 0 }}>
                  <Text type="secondary" style={{ fontFamily: 'monospace', fontSize: 12 }}>{row.path}</Text>
                </div>
                <div style={{ flex: 1, fontFamily: 'monospace', fontSize: 13, color: '#595959', wordBreak: 'break-all' }}>
                  {readonlyDisplay(row.value)}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* ── Модалка учётных данных (SSH/WinRM) ── */}
      <Modal
        title={`Учётные данные для ${hostname ?? 'устройства'}`}
        open={credOpen}
        onCancel={() => setCredOpen(false)}
        onOk={submitWithCredentials}
        okText="Отправить"
        confirmLoading={submitting}
        okButtonProps={{ icon: <SendOutlined /> }}
        destroyOnClose
      >
        <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
          Для применения конфигурации по {deviceType === 'ПК' ? 'WinRM' : 'SSH'} требуются учётные данные администратора устройства.
        </Text>
        <Form form={credForm} layout="vertical" initialValues={{ port: deviceType === 'ПК' ? 5985 : 22 }}>
          <Form.Item name="username" label="Логин" rules={[{ required: true, message: 'Укажите логин' }]}>
            <Input autoComplete="off" />
          </Form.Item>
          <Form.Item name="password" label="Пароль" rules={[{ required: true, message: 'Укажите пароль' }]}>
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item name="port" label="Порт">
            <InputNumber min={1} max={65535} style={{ width: 160 }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
