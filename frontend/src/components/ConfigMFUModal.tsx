import { Button, Form, Input, InputNumber, Modal, Select, Switch, message, Tabs, Divider, Space } from 'antd';
import React, { useState } from 'react';

const { TabPane } = Tabs;

interface Props {
    open: boolean;
    onClose: () => void;
    hostname?: string;
    deviceId?: number;
}

export default function ConfigMFUModal({ open, onClose, hostname, deviceId }: Props) {
    const [form] = Form.useForm();
    const [submitting, setSubmitting] = useState(false);
    const [activeTab, setActiveTab] = useState('basic');

    const onFinish = async (values: Record<string, any>) => {
        if (!deviceId) return message.error('Не выбрано устройство');
        setSubmitting(true);
        try {
            // Важно: на бэкенде нужно будет добавить логику, которая на основе
            // brand (из переданных params) будет формировать правильные OID и SNMP-запросы.
            await fetch('/api/devices/configure', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ deviceId, channel: 'snmp', target: 'mfu', params: values }),
            });
            message.success('SNMP-профиль МФУ отправлен');
            form.resetFields();
            onClose();
        } catch {
            message.error('Ошибка отправки SNMP-настроек');
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Modal
            title={`SNMP конфигурация МФУ: ${hostname || 'device'}`}
            open={open}
            onCancel={onClose}
            footer={null}
            width={650}
            destroyOnClose
        >
            <Form
                form={form}
                layout="vertical"
                onFinish={onFinish}
                initialValues={{
                    brand: 'kyocera',
                    location: '',
                    contact: '',
                    // Основные настройки
                    trayMode: 'auto',
                    duplex: 'long-edge',
                    tonerSave: true,
                    powerSaveMinutes: 10,
                    // Расширенные настройки (примеры)
                    deviceName: '',
                    paperSize: 'A4',
                    paperType: 'plain',
                    copies: 1,
                    resolution: '600dpi',
                    // Настройки безопасности
                    snmpEnabled: true,
                    communityRead: 'public',
                    communityWrite: 'private',
                    authProtocol: 'MD5',
                    privProtocol: 'DES',
                    // Настройки оповещений
                    emailAlerts: false,
                    alertEmail: '',
                    trapDestinations: '',
                }}
            >
                {/* Выбор бренда - критически важно */}
                <Form.Item label="Бренд устройства" name="brand" rules={[{ required: true }]}>
                    <Select
                        options={[
                            { value: 'kyocera', label: 'Kyocera' },
                            { value: 'samsung', label: 'Samsung' },
                            { value: 'canon', label: 'Canon' },
                        ]}
                    />
                </Form.Item>

                <Tabs activeKey={activeTab} onChange={setActiveTab}>
                    <TabPane tab="Основные" key="basic">
                        <Form.Item
                            label="Местоположение (sysLocation)"
                            name="location"
                            tooltip="MIB-II: .1.3.6.1.2.1.1.6.0"
                            rules={[{ required: true }]}
                        >
                            <Input placeholder="3 этаж, кабинет 12" />
                        </Form.Item>

                        <Form.Item
                            label="Контактное лицо (sysContact)"
                            name="contact"
                            tooltip="MIB-II: .1.3.6.1.2.1.1.4.0"
                        >
                            <Input placeholder="admin@example.com" />
                        </Form.Item>

                        <Divider orientation="vertical">Печать</Divider>

                        <Form.Item label="Режим подачи бумаги" name="trayMode" rules={[{ required: true }]}>
                            <Select
                                options={[
                                    { value: 'auto', label: 'Автовыбор' },
                                    { value: 'tray1', label: 'Лоток 1' },
                                    { value: 'tray2', label: 'Лоток 2' },
                                    { value: 'tray3', label: 'Лоток 3' },
                                    { value: 'mpTray', label: 'Многоцелевой лоток' },
                                ]}
                            />
                        </Form.Item>

                        <Form.Item label="Формат бумаги" name="paperSize">
                            <Select
                                options={[
                                    { value: 'A4', label: 'A4' },
                                    { value: 'A3', label: 'A3' },
                                    { value: 'Letter', label: 'Letter' },
                                    { value: 'Legal', label: 'Legal' },
                                ]}
                            />
                        </Form.Item>

                        <Form.Item label="Тип бумаги" name="paperType">
                            <Select
                                options={[
                                    { value: 'plain', label: 'Обычная' },
                                    { value: 'thick', label: 'Плотная' },
                                    { value: 'envelope', label: 'Конверт' },
                                    { value: 'recycled', label: 'Переработанная' },
                                ]}
                            />
                        </Form.Item>

                        <Form.Item label="Двусторонняя печать" name="duplex" rules={[{ required: true }]}>
                            <Select
                                options={[
                                    { value: 'off', label: 'Отключена' },
                                    { value: 'long-edge', label: 'По длинной стороне' },
                                    { value: 'short-edge', label: 'По короткой стороне' },
                                ]}
                            />
                        </Form.Item>

                        <Form.Item
                            label="Режим экономии тонера"
                            name="tonerSave"
                            valuePropName="checked"
                            tooltip="Kyocera: EcoPrint"
                        >
                            <Switch />
                        </Form.Item>

                        <Form.Item label="Качество печати (dpi)" name="resolution">
                            <Select
                                options={[
                                    { value: '300dpi', label: '300 x 300 dpi' },
                                    { value: '600dpi', label: '600 x 600 dpi' },
                                    { value: '1200dpi', label: '1200 x 1200 dpi' },
                                ]}
                            />
                        </Form.Item>

                        <Form.Item label="Количество копий" name="copies">
                            <InputNumber min={1} max={999} style={{ width: '100%' }} />
                        </Form.Item>

                        <Form.Item
                            label="Энергосбережение (минут)"
                            name="powerSaveMinutes"
                            rules={[{ required: true }]}
                            tooltip="Kyocera: Sleep Mode; Samsung: Power Save"
                        >
                            <InputNumber min={1} max={240} style={{ width: '100%' }} />
                        </Form.Item>
                    </TabPane>

                    <TabPane tab="Сеть и SNMP" key="network">
                        <Form.Item
                            label="Имя устройства (sysName)"
                            name="deviceName"
                            tooltip="MIB-II: .1.3.6.1.2.1.1.5.0"
                        >
                            <Input placeholder="MFU-001" />
                        </Form.Item>

                        <Form.Item label="Включить SNMP" name="snmpEnabled" valuePropName="checked">
                            <Switch />
                        </Form.Item>

                        {Form.useWatch('snmpEnabled', form) && (
                            <>
                                <Form.Item
                                    label="SNMP Read Community"
                                    name="communityRead"
                                    tooltip="По умолчанию 'public'"
                                >
                                    <Input.Password placeholder="public" />
                                </Form.Item>
                                <Form.Item
                                    label="SNMP Write Community"
                                    name="communityWrite"
                                    tooltip="По умолчанию 'private'"
                                >
                                    <Input.Password placeholder="private" />
                                </Form.Item>

                                <Space direction="vertical" style={{ width: '100%' }}>
                                    <Form.Item
                                        label="SNMPv3 Auth Protocol"
                                        name="authProtocol"
                                    >
                                        <Select
                                            options={[
                                                { value: 'MD5', label: 'MD5' },
                                                { value: 'SHA', label: 'SHA' },
                                            ]}
                                        />
                                    </Form.Item>
                                    <Form.Item
                                        label="SNMPv3 Priv Protocol"
                                        name="privProtocol"
                                    >
                                        <Select
                                            options={[
                                                { value: 'DES', label: 'DES' },
                                                { value: 'AES', label: 'AES' },
                                            ]}
                                        />
                                    </Form.Item>
                                </Space>
                            </>
                        )}
                    </TabPane>

                    <TabPane tab="Оповещения" key="alerts">
                        <Form.Item
                            label="Email-оповещения о событиях"
                            name="emailAlerts"
                            valuePropName="checked"
                        >
                            <Switch />
                        </Form.Item>

                        {Form.useWatch('emailAlerts', form) && (
                            <Form.Item label="Email для оповещений" name="alertEmail">
                                <Input type="email" placeholder="admin@example.com" />
                            </Form.Item>
                        )}

                        <Form.Item
                            label="SNMP Trap destinations"
                            name="trapDestinations"
                            tooltip="IP-адреса для отправки SNMP-ловушек, через запятую"
                        >
                            <Input placeholder="192.168.1.100, 192.168.1.101" />
                        </Form.Item>
                    </TabPane>
                </Tabs>

                <Button type="primary" htmlType="submit" loading={submitting} block>
                    Применить
                </Button>
            </Form>
        </Modal>
    );
}