import React, { useEffect, useState } from 'react';
import { Space, Tag, Button, Select, Typography, message } from 'antd';
import { ReloadOutlined, FileTextOutlined, DatabaseOutlined } from '@ant-design/icons';
import { configApi } from '../api/configApi';
import { templatesApi, Template } from '../api/templatesApi';

const { Text } = Typography;

interface Props {
  open: boolean;
  deviceId?: number;
  deviceHostname?: string;
  deviceIp?: string;
  /** Вызывается с источником значений (активная конфигурация или содержимое шаблона). */
  onPrefill: (source: Record<string, any>) => void;
}

/**
 * Панель над редактором конфигурации: при открытии подставляет значения из
 * текущей (активной) конфигурации устройства и позволяет загрузить значения из
 * шаблона. Переменные шаблона ({{device.hostname}}, {{device.ip}}) подставляются
 * под выбранное устройство.
 */
export default function ConfigPrefillBar({ open, deviceId, deviceHostname, deviceIp, onPrefill }: Props) {
  const [templates, setTemplates] = useState<Template[]>([]);
  const [hasActive, setHasActive] = useState<boolean | null>(null);

  const substituteVars = (content: Record<string, any>): Record<string, any> => {
    let json = JSON.stringify(content);
    if (deviceHostname) json = json.split('{{device.hostname}}').join(deviceHostname);
    if (deviceIp) json = json.split('{{device.ip}}').join(deviceIp);
    try { return JSON.parse(json); } catch { return content; }
  };

  const loadActive = async (silent = false) => {
    if (!deviceId) return;
    const active = await configApi.getActiveConfig(deviceId);
    if (active && Object.keys(active).length > 0) {
      setHasActive(true);
      onPrefill(active);
      if (!silent) message.success('Значения загружены из текущей конфигурации устройства');
    } else {
      setHasActive(false);
      if (!silent) message.info('У устройства нет сохранённой конфигурации');
    }
  };

  useEffect(() => {
    if (!open || !deviceId) return;
    let cancelled = false;
    (async () => {
      try {
        const tpls = await templatesApi.getAll().catch(() => []);
        if (!cancelled) setTemplates(tpls);
        if (!cancelled) await loadActive(true); // авто-предзаполнение при открытии
      } catch { /* префилл не критичен */ }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, deviceId]);

  const applyTemplate = (templateId: number) => {
    const tpl = templates.find(t => t.id === templateId);
    if (!tpl) return;
    onPrefill(substituteVars(tpl.content));
    message.success(`Значения загружены из шаблона «${tpl.name}»`);
  };

  return (
    <div style={{
      display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 8,
      marginBottom: 12, padding: '8px 12px',
      background: '#f5f7fa', borderRadius: 8, border: '1px solid #e8e8e8',
    }}>
      <Space size={6} wrap>
        <DatabaseOutlined style={{ color: '#1677ff' }} />
        <Text type="secondary">Источник значений:</Text>
        {hasActive === null ? (
          <Tag>загрузка…</Tag>
        ) : hasActive ? (
          <Tag color="blue">текущая конфигурация устройства</Tag>
        ) : (
          <Tag>нет сохранённой конфигурации</Tag>
        )}
      </Space>
      <div style={{ marginLeft: 'auto', display: 'flex', gap: 8, alignItems: 'center' }}>
        <Button size="small" icon={<ReloadOutlined />} disabled={!deviceId} onClick={() => loadActive(false)}>
          Из устройства
        </Button>
        <Select
          size="small"
          placeholder="Из шаблона"
          style={{ minWidth: 200 }}
          allowClear
          suffixIcon={<FileTextOutlined />}
          onChange={(v) => v && applyTemplate(v)}
          options={templates.map(t => ({ value: t.id, label: t.name }))}
        />
      </div>
    </div>
  );
}
