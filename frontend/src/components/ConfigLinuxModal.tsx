import { Button, Form, Input, Modal } from 'antd';
import React from 'react';

interface Props {
  open: boolean;
  onClose: () => void;
  hostname?: string;
}

export default function ConfigLinuxModal({ open, onClose, hostname }: Props) {
  return (
    <Modal title={`Linux config: ${hostname || 'device'}`} open={open} onCancel={onClose} footer={null} destroyOnClose>
      <Form layout="vertical" initialValues={{ option: 'sysctl net.ipv4.ip_forward', value: '1' }}>
        <Form.Item label="Опция" name="option" rules={[{ required: true }]}>
          <Input placeholder="ключ или путь настройки" />
        </Form.Item>
        <Form.Item label="Значение" name="value" rules={[{ required: true }]}>
          <Input.TextArea rows={3} placeholder="значение настройки" />
        </Form.Item>
        <Button type="primary"> Применить </Button>
      </Form>
    </Modal>
  );
}
