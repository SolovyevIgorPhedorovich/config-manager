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
            <Form layout='vertical'>
                <Form.Item label="Команда/плейбук" name="commands" rules={[{ required: true }]}>
                    <Input.TextArea rows={5} placeholder={"sysctl -w net.ipv4.ip_forward=1"} />
                </Form.Item>
                <Button type='primary'> Применить </Button>
            </Form>
        </Modal>
    )
}
