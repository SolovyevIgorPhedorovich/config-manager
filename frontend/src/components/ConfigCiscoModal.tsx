import React, { useState } from 'react';
import {
    Button, Form, InputNumber, Modal, Select, Switch, message,
    Input, Divider, Tabs, Space, Collapse
} from 'antd';

interface Props {
    open: boolean;
    onClose: () => void;
    hostname?: string;
    deviceId?: number;
}

export default function ConfigCiscoModal({ open, onClose, hostname, deviceId }: Props) {
    const [form] = Form.useForm();
    const [submitting, setSubmitting] = useState(false);
    const mode = Form.useWatch('mode', form);
    const stormControlEnabled = Form.useWatch('stormControl', form);

    const onFinish = async (values: Record<string, any>) => {
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

    const tabItems = [
        {
            key: 'basic',
            label: 'Основные настройки',
            children: (
                <>
                    <Form.Item label="Описание интерфейса" name="description">
                        <Input placeholder="Например: 'To Server' или 'Uplink to Core'" />
                    </Form.Item>
                    <Form.Item label="Административное состояние" name="shutdown" valuePropName="checked">
                        <Switch checkedChildren="Вкл (shutdown)" unCheckedChildren="Выкл (no shutdown)" />
                    </Form.Item>
                    <Divider>Физические параметры</Divider>
                    <Space wrap>
                        <Form.Item label="Speed" name="speed" style={{ width: 180 }}>
                            <Select options={[
                                { value: 'auto', label: 'Auto' },
                                { value: '10', label: '10 Mbps' },
                                { value: '100', label: '100 Mbps' },
                                { value: '1000', label: '1 Gbps' },
                            ]} />
                        </Form.Item>
                        <Form.Item label="Duplex" name="duplex" style={{ width: 150 }}>
                            <Select options={[
                                { value: 'auto', label: 'Auto' },
                                { value: 'full', label: 'Full' },
                                { value: 'half', label: 'Half' },
                            ]} />
                        </Form.Item>
                    </Space>
                </>
            ),
        },
        {
            key: 'port',
            label: 'Настройки портов',
            children: (
                <>
                    <Form.Item
                        label="Интерфейс(ы)"
                        name="interfaceNames"
                        rules={[{ required: true, message: 'Выберите хотя бы один интерфейс' }]}
                    >
                        <Select
                            mode="multiple"
                            allowClear
                            placeholder="Выберите один или несколько интерфейсов"
                            options={[
                                { value: 'GigabitEthernet0/1' },
                                { value: 'GigabitEthernet0/2' },
                                { value: 'GigabitEthernet0/3' },
                                { value: 'FastEthernet0/1' },
                                { value: 'TenGigabitEthernet0/1' },
                                { value: 'Port-channel1' },
                            ]}
                        />
                    </Form.Item>
                    <Form.Item label="Режим порта" name="mode" rules={[{ required: true }]}>
                        <Select options={[
                            { value: 'access', label: 'Access' },
                            { value: 'trunk', label: 'Trunk' },
                        ]} />
                    </Form.Item>
                    {mode === 'access' ? (
                        <>
                            <Form.Item label="VLAN ID (Access)" name="vlanId" rules={[{ required: true }]}>
                                <InputNumber min={1} max={4094} style={{ width: '100%' }} />
                            </Form.Item>
                            <Form.Item label="Portfast" name="portfast" valuePropName="checked" tooltip="Ускоряет переход порта в forwarding состояние">
                                <Switch checkedChildren="Вкл" unCheckedChildren="Выкл" />
                            </Form.Item>
                        </>
                    ) : (
                        <>
                            <Form.Item label="Native VLAN" name="nativeVlan" tooltip="VLAN для нетегированного трафика">
                                <InputNumber min={1} max={4094} style={{ width: '100%' }} />
                            </Form.Item>
                            <Form.Item
                                label="Allowed VLANs"
                                name="allowedVlans"
                                tooltip="Пример: 10,20,30-40 (оставьте пустым для всех)"
                            >
                                <Input placeholder="10,20,30-40" />
                            </Form.Item>
                        </>
                    )}
                </>
            ),
        },
        {
            key: 'security',
            label: 'Безопасность',
            children: (
                <>
                    <Form.Item label="Port Security" name="enablePortSecurity" valuePropName="checked">
                        <Switch />
                    </Form.Item>
                    <Collapse
                        ghost
                        items={[{
                            key: '1',
                            label: 'Дополнительные настройки безопасности',
                            children: (
                                <>
                                    <Form.Item label="Storm Control" name="stormControl" valuePropName="checked">
                                        <Switch />
                                    </Form.Item>
                                    {stormControlEnabled && (
                                        <Form.Item
                                            label="Порог Storm Control (%)"
                                            name="stormControlLevel"
                                            tooltip="Процент от полосы пропускания"
                                        >
                                            <InputNumber min={1} max={100} step={1} style={{ width: '100%' }} />
                                        </Form.Item>
                                    )}
                                </>
                            ),
                        }]}
                    />
                    <Divider>BPDU Guard / Filter (опционально)</Divider>
                    <Form.Item label="BPDU Guard" name="bpduGuard" valuePropName="checked" tooltip="Блокирует порт при получении BPDU (для access портов)">
                        <Switch />
                    </Form.Item>
                    <Form.Item label="BPDU Filter" name="bpduFilter" valuePropName="checked" tooltip="Отфильтровывает BPDU кадры на порту">
                        <Switch />
                    </Form.Item>
                </>
            ),
        },
    ];

    return (
        <Modal
            title={`Cisco config: ${hostname || 'device'}`}
            open={open}
            onCancel={onClose}
            footer={null}
            width={700}
            destroyOnClose
        >
            <Form
                form={form}
                layout="vertical"
                onFinish={onFinish}
                initialValues={{
                    interfaceNames: ['GigabitEthernet0/1'],
                    mode: 'access',
                    vlanId: 10,
                    enablePortSecurity: false,
                    shutdown: false,
                    description: '',
                    speed: 'auto',
                    duplex: 'auto',
                    nativeVlan: 1,
                    allowedVlans: '',
                    portfast: true,
                    stormControl: false,
                    stormControlLevel: 10,
                }}
            >
                <Tabs defaultActiveKey="basic" type="card" items={tabItems} />
                <Button type="primary" htmlType="submit" loading={submitting} block style={{ marginTop: 16 }}>
                    Применить
                </Button>
            </Form>
        </Modal>
    );
}
