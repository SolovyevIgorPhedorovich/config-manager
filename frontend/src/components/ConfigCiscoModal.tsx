import { Button, Form, Input, Modal } from 'antd';
import React from 'react';

interface Props {
    open: boolean;
    onClose: () => void;
    hostname?: string;
}

export default function ConfigCiscoModal({ open, onClose, hostname }: Props) {
    return (
        <Modal title={`Cisco config: ${hostname || 'device'}`} open={open} onCancel={onClose} footer={null} destroyOnClose>
            <Form layout='vertical'>
                <Form.Item label="Конфигурация команды" name="commands" rules={[{ required: true }]}>
                    <Input.TextArea rows={6} placeholder={"interface Gi0/1\ndescription Uplink"} />
                </Form.Item>
                <Button type='primary'> Применить </Button>
            </Form>
        </Modal>
    )
}
