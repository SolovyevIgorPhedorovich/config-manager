import { Button, Form, Input, Modal } from 'antd';
import React from 'react';

interface Props {
    open: boolean;
    onClose: () => void;
    hostname?: string;
}

export default function ConfigMFUModal({ open, onClose, hostname }: Props) {
    return (
        <Modal title={`МФУ SNMP config: ${hostname || 'device'}`} open={open} onCancel={onClose} footer={null} destroyOnClose>
            <Form layout='vertical'>
                <Form.Item label="OID" name="oid" rules={[{ required: true }]}>
                    <Input placeholder='1.3.6.1...' />
                </Form.Item>
                <Form.Item label="Value" name='value' rules={[{ required: true }]}>
                    <Input placeholder='New Value' />
                </Form.Item>
                <Button type='primary'> Применить </Button>
            </Form>
        </Modal>
    )
}
