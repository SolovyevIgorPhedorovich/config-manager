import React, { useState } from 'react';
import { Modal, Form, InputNumber, Input, Select, Button, message, Alert } from 'antd';

export interface ScanOptions {
  ipaddr: string;
  mask: number;
  port: number;
  community: string;
  snmpv: string;
}

const SCAN_MODE_LABELS: Record<string, string> = {
  all: 'Все устройства',
  windows: 'ПК (Windows)',
  pc: 'ПК (Windows)',
  linux: 'Linux / VM',
  vm: 'Linux / VM',
  cisco: 'Сетевые устройства Cisco',
  mfu: 'МФУ',
};

export const ScanDeviceModal: React.FC<{
  open: boolean;
  onCancel: () => void;
  onScan: (options: ScanOptions) => Promise<void>;
  scanMode?: string;
}> = ({ open, onCancel, onScan, scanMode }) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);
      await onScan(values as ScanOptions);
      onCancel();
    } catch (error) {
      console.error(error);
      message.error('Ошибка запуска сканирования');
    } finally {
      setLoading(false);
    }
  };

  const modeLabel = scanMode ? (SCAN_MODE_LABELS[scanMode] ?? scanMode) : SCAN_MODE_LABELS['all'];

  return (
    <Modal
      title="Автоматический поиск устройств"
      open={open}
      onCancel={onCancel}
      footer={[
        <Button key="cancel" onClick={onCancel}>Отмена</Button>,
        <Button key="submit" type="primary" loading={loading} onClick={handleSubmit}>
          Запустить сканирование
        </Button>,
      ]}
    >
      <Alert
        style={{ marginBottom: 16 }}
        type="info"
        showIcon
        message={`Режим сканирования: ${modeLabel}`}
        description={
          scanMode && scanMode !== 'all'
            ? 'В базу данных будут добавлены только устройства выбранного типа.'
            : 'В базу данных будут добавлены все обнаруженные устройства.'
        }
      />
      <Form form={form} layout="vertical">
        <Form.Item
          name="ipaddr"
          label="Сетевой адрес (начало)"
          initialValue="192.168.1.1"
          rules={[{ required: true, message: 'Введите IP-адрес' }]}
        >
          <Input placeholder="Например: 192.168.1.1" />
        </Form.Item>

        <Form.Item
          name="mask"
          label="Маска сети (CIDR)"
          initialValue={24}
          tooltip="Рекомендуется /24 для локальной сети. Максимум /24 (256 хостов)."
          rules={[{ required: true, message: 'Введите маску' }]}
        >
          <InputNumber min={0} max={24} style={{ width: '100%' }} />
        </Form.Item>

        <Form.Item
          name="port"
          label="SNMP-порт"
          initialValue={161}
          tooltip="Стандартный порт SNMP — 161"
        >
          <InputNumber disabled style={{ width: '100%' }} />
        </Form.Item>

        <Form.Item
          name="community"
          label="Community string"
          initialValue="public"
          tooltip="Для безопасности используйте кастомную community (не public!)"
        >
          <Input />
        </Form.Item>

        <Form.Item
          name="snmpv"
          label="Версия SNMP"
          initialValue="v2c"
          rules={[{ required: true, message: 'Выберите версию SNMP' }]}
        >
          <Select>
            <Select.Option value="v1">SNMP v1</Select.Option>
            <Select.Option value="v2c">SNMP v2c (рекомендуется)</Select.Option>
            <Select.Option value="v3">SNMP v3</Select.Option>
          </Select>
        </Form.Item>
      </Form>
    </Modal>
  );
};
