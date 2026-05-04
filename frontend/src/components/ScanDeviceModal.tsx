// src/components/ScanDeviceModal.tsx

import React, { useState } from 'react';
import { Modal, Form, InputNumber, Input, Select, Button, message, Typography } from 'antd';

const { Text } = Typography;

interface ScanOptions {
  ipaddr: string;
  mask: number;
  port: number;
  community: string;
  snmpv: string;
}

export const ScanDeviceModal: React.FC<{
  open: boolean;
  onCancel: () => void;
  onScan: (options: ScanOptions) => Promise<void>;
}> = ({ open, onCancel, onScan }) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);
      await onScan(values as ScanOptions);
      message.success('Сканирование запущено. Подождите...');
      onCancel();
    } catch (error) {
      console.error(error);
      message.error('Ошибка');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title="🔍 Автоматический поиск устройств"
      open={open}
      onCancel={onCancel}
      footer={[
        <Button key="cancel" onClick={onCancel}>Отмена</Button>,
        <Button key="submit" type="primary" loading={loading} onClick={handleSubmit}>
          Запустить сканирование
        </Button>
      ]}
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="ipaddr"
          label="Сетевой адрес (начало)"
          initialValue="192.168.1.1"
          rules={[{ required: true, message: 'Введите IP' }]}
        >
          <Input placeholder="Например: 192.168.1.1" />
        </Form.Item>

        <Form.Item
          name="mask"
          label="Маска сети (CIDR)"
          initialValue={24}
          tooltip="Рекомендуется /24 для локальной сети"
          rules={[{ required: true, message: 'Введите маску' }]}
        >
          <InputNumber min={0} max={30} />
        </Form.Item>

        <Form.Item
          name="port"
          label="SNMP-порт"
          initialValue={161}
          tooltip="Стандартный порт SNMP — 161"
        >
          <InputNumber disabled />
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
          rules={[{ required: true }]}
        >
          <Select>
            <option value="v1">SNMP v1</option>
            <Select.Option value="v2c">SNMP v2c (рекомендуется)</Select.Option>
             <option value="v3">SNMP v3</option>
          </Select>
        </Form.Item>
      </Form>
    </Modal>
  );
};
