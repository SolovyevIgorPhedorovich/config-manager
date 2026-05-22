import React from 'react';
import { Modal, Form, Input, Button } from 'antd';

interface Props {
  open: boolean;
  onClose: () => void;
  hostname?: string;
}

export default function ConfigWindowsModal({ open, onClose, hostname }: Props) {
  return (
    <Modal title={`Windows config: ${hostname || 'device'}`} open={open} onCancel={onClose} footer={null} destroyOnClose>
      <Form layout="vertical">
        <Form.Item label="PowerShell command" name="ps" rules={[{ required: true }]}>
          <Input.TextArea rows={5} placeholder="Set-ItemProperty ..." />
        </Form.Item>
        <Button type="primary">Применить через WinRM</Button>
      </Form>
    </Modal>
  );
}
