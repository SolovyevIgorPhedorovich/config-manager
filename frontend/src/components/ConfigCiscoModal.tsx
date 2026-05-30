import { Button, Form, InputNumber, Modal, Select, Switch, message } from 'antd';
import React from 'react';

interface Props {
  open: boolean;
  onClose: () => void;
  hostname?: string;
  deviceId?: number;
}

export default function ConfigCiscoModal({ open, onClose, hostname, deviceId }: Props) {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = React.useState(false);

  const onFinish = async (values: Record<string, unknown>) => {
    if (!deviceId) return message.error('Не выбрано устройство');

    setSubmitting(true);
    try {
      await fetch('/api/devices/configure', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ deviceId, channel: 'ssh', target: 'cisco', params: values }),
      });
      message.success('Cisco-конфигурация отправлена');
      form.resetFields();
      onClose();
    } catch {
      message.error('Ошибка отправки Cisco-конфигурации');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal title={`Cisco config: ${hostname || 'device'}`} open={open} onCancel={onClose} footer={null} destroyOnClose>
      <Form
        form={form}
        layout="vertical"
        onFinish={onFinish}
        initialValues={{
          interfaceNames: ['GigabitEthernet0/1'],
          vlanId: 10,
          mode: 'access',
          enablePortSecurity: false,
          adminUp: true,
        }}
      >
        <Form.Item label="Интерфейсы" name="interfaceNames" rules={[{ required: true, message: 'Выберите один или несколько портов' }]}>
          <Select
            mode="multiple"
            placeholder="Выберите порты"
            options={[
              { value: 'GigabitEthernet0/1' },
              { value: 'GigabitEthernet0/2' },
              { value: 'GigabitEthernet0/3' },
              { value: 'FastEthernet0/1' },
              { value: 'FastEthernet0/2' },
            ]}
          />
        </Form.Item>
        <Form.Item label="Режим порта" name="mode" rules={[{ required: true }]}>
          <Select options={[{ value: 'access', label: 'Access' }, { value: 'trunk', label: 'Trunk' }]} />
        </Form.Item>
        <Form.Item label="VLAN" name="vlanId" rules={[{ required: true }]}>
          <InputNumber min={1} max={4094} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item label="Port-security" name="enablePortSecurity" valuePropName="checked">
          <Switch />
        </Form.Item>
        <Form.Item label="Административно включить порты" name="adminUp" valuePropName="checked">
          <Switch />
        </Form.Item>
        <Button type="primary" htmlType="submit" loading={submitting}> Применить </Button>
      </Form>
    </Modal>
  );
}
