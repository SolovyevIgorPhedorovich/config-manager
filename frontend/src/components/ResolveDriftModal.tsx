import React, { useEffect, useState } from 'react';
import { Modal, Button, Space, Alert, Form, Input, InputNumber, Typography, message, Spin } from 'antd';
import { CloudDownloadOutlined, CloudUploadOutlined } from '@ant-design/icons';
import { configApi, DriftResolution } from '../api/configApi';
import ConfigDiffViewer from './ConfigDiffViewer';

const { Text, Paragraph } = Typography;

interface DriftDevice {
  id: number;
  hostname: string;
  operatingSystem?: 'linux' | 'windows';
  typeCode?: number;
}

interface Props {
  open: boolean;
  device: DriftDevice | null;
  onClose: () => void;
  onResolved: () => void;
}

/**
 * Разрешение расхождения конфигурации: показывает diff сохранённой (stored)
 * и фактической (actual) конфигурации и предлагает две резолюции —
 * принять фактическую как активную либо повторно применить сохранённую.
 */
export default function ResolveDriftModal({ open, device, onClose, onResolved }: Props) {
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [stored, setStored] = useState<Record<string, any> | null>(null);
  const [actual, setActual] = useState<Record<string, any> | null>(null);
  const [credForm] = Form.useForm();
  const [needCreds, setNeedCreds] = useState(false);

  // МФУ настраивается по SNMP без логина/пароля; остальным нужны учётные данные
  const credsRequired = device?.typeCode !== 1;
  const defaultPort = device?.operatingSystem === 'windows' ? 5985 : 22;

  useEffect(() => {
    if (!open || !device) return;
    setNeedCreds(false);
    credForm.resetFields();
    setLoading(true);
    configApi.getDrift(device.id)
      .then(d => { setStored(d.stored); setActual(d.actual); })
      .catch((e: any) => message.error(e?.response?.data?.message || 'Не удалось загрузить расхождение'))
      .finally(() => setLoading(false));
  }, [open, device]);

  const resolve = async (resolution: DriftResolution, credentials?: any) => {
    if (!device) return;
    setSubmitting(true);
    try {
      const res = await configApi.resolveDrift(device.id, resolution, credentials);
      const scheduled = res.data?.scheduledDeviceIds?.length;
      message.success(
        resolution === 'ACCEPT_DEVICE'
          ? 'Фактическая конфигурация принята как активная'
          : scheduled
            ? 'Устройство офлайн — повторное применение запланировано'
            : 'Сохранённая конфигурация повторно применяется',
      );
      onResolved();
      onClose();
    } catch (e: any) {
      message.error(e?.response?.data?.message || 'Не удалось разрешить расхождение');
    } finally {
      setSubmitting(false);
    }
  };

  const handleReapply = async () => {
    if (credsRequired && !needCreds) { setNeedCreds(true); return; }
    if (credsRequired) {
      let vals: any;
      try { vals = await credForm.validateFields(); } catch { return; }
      resolve('REAPPLY_STORED', { username: vals.username, password: vals.password, port: vals.port });
    } else {
      resolve('REAPPLY_STORED');
    }
  };

  return (
    <Modal
      title={`Расхождение конфигурации: ${device?.hostname ?? ''}`}
      open={open}
      onCancel={onClose}
      width={900}
      destroyOnClose
      footer={
        <Space>
          <Button onClick={onClose}>Отмена</Button>
          <Button icon={<CloudDownloadOutlined />} loading={submitting}
            onClick={() => resolve('ACCEPT_DEVICE')}>
            Принять с устройства
          </Button>
          <Button type="primary" icon={<CloudUploadOutlined />} loading={submitting}
            onClick={handleReapply}>
            Применить сохранённую
          </Button>
        </Space>
      }
    >
      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 12 }}
        message="Фактическая конфигурация устройства разошлась с сохранённой версией"
        description="«Принять с устройства» сделает фактическую конфигурацию активной версией. «Применить сохранённую» повторно отправит сохранённую конфигурацию на устройство (офлайн — встанет в очередь)."
      />

      {loading ? (
        <div style={{ textAlign: 'center', padding: 32 }}><Spin /></div>
      ) : (
        <ConfigDiffViewer
          leftConfig={stored}
          rightConfig={actual}
          leftLabel="Сохранённая (активная)"
          rightLabel="Фактическая (на устройстве)"
        />
      )}

      {needCreds && credsRequired && (
        <div style={{ marginTop: 16, padding: 12, background: '#fafafa', borderRadius: 6, border: '1px solid #eee' }}>
          <Paragraph style={{ marginBottom: 8 }}>
            <Text strong>Учётные данные для повторного применения</Text>
          </Paragraph>
          <Form form={credForm} layout="inline" initialValues={{ port: defaultPort }}>
            <Form.Item name="username" label="Логин" rules={[{ required: true, message: 'Укажите логин' }]}>
              <Input autoComplete="off" />
            </Form.Item>
            <Form.Item name="password" label="Пароль" rules={[{ required: true, message: 'Укажите пароль' }]}>
              <Input.Password autoComplete="new-password" />
            </Form.Item>
            <Form.Item name="port" label="Порт">
              <InputNumber min={1} max={65535} />
            </Form.Item>
          </Form>
          <Text type="secondary" style={{ fontSize: 12 }}>Нажмите «Применить сохранённую» ещё раз для подтверждения.</Text>
        </div>
      )}
    </Modal>
  );
}
