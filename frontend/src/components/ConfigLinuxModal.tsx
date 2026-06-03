import { Button, Form, InputNumber, Modal, Select, Switch, message, Input, Tabs } from 'antd';
import React, { useState } from 'react';

interface Props {
    open: boolean;
    onClose: () => void;
    hostname?: string;
    deviceId?: number;
}

export default function ConfigLinuxModal({ open, onClose, hostname, deviceId }: Props) {
    const [form] = Form.useForm();
    const [submitting, setSubmitting] = useState(false);
    const [activeTab, setActiveTab] = useState('system');
    const autoUpgrade = Form.useWatch('autoUpgrade', form);
    const createUser = Form.useWatch('createUser', form);

    const onFinish = async (values: any) => {
        if (!deviceId) return message.error('Не выбрано устройство');
        setSubmitting(true);
        try {
            await fetch('/api/devices/configure', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    deviceId,
                    channel: 'ssh',
                    target: 'linux',
                    params: values,
                }),
            });
            message.success('Linux-конфигурация отправлена');
            form.resetFields();
            onClose();
        } catch {
            message.error('Ошибка отправки Linux-конфигурации');
        } finally {
            setSubmitting(false);
        }
    };

    const tabItems = [
        {
            key: 'system',
            label: 'Система',
            children: (
                <>
                    <Form.Item label="Имя хоста (hostname)" name="hostname">
                        <Input placeholder="web-01.example.com" />
                    </Form.Item>
                    <Form.Item label="Часовой пояс" name="timezone" rules={[{ required: true }]}>
                        <Select
                            showSearch
                            options={[
                                { value: 'UTC', label: 'UTC' },
                                { value: 'Europe/Moscow', label: 'Europe/Moscow (MSK)' },
                                { value: 'Europe/London', label: 'Europe/London' },
                                { value: 'America/New_York', label: 'America/New_York' },
                                { value: 'Asia/Tokyo', label: 'Asia/Tokyo' },
                                { value: 'Asia/Almaty', label: 'Asia/Almaty' },
                            ]}
                        />
                    </Form.Item>
                    <Form.Item label="NTP-серверы" name="ntpServers" tooltip="Пробел — разделитель">
                        <Input placeholder="0.pool.ntp.org 1.pool.ntp.org" />
                    </Form.Item>
                    <Form.Item label="Автоматическое обновление" name="autoUpgrade" valuePropName="checked">
                        <Switch />
                    </Form.Item>
                    {autoUpgrade && (
                        <Form.Item label="Политика обновлений" name="upgradePolicy" rules={[{ required: true }]}>
                            <Select
                                options={[
                                    { value: 'security', label: 'Только security' },
                                    { value: 'all', label: 'Все обновления' },
                                ]}
                            />
                        </Form.Item>
                    )}
                </>
            ),
        },
        {
            key: 'network',
            label: 'Сеть',
            children: (
                <>
                    <Form.Item label="DNS-серверы" name="dnsServers" tooltip="Пробел — разделитель">
                        <Input placeholder="8.8.8.8 8.8.4.4" />
                    </Form.Item>
                    <Form.Item label="SSH порт" name="sshPort" rules={[{ required: true }]}>
                        <InputNumber min={1} max={65535} style={{ width: '100%' }} />
                    </Form.Item>
                    <Form.Item label="Разрешить аутентификацию по паролю SSH" name="sshPasswordAuth" valuePropName="checked">
                        <Switch />
                    </Form.Item>
                    <Form.Item label="Разрешить вход root по SSH" name="sshRootLogin" valuePropName="checked">
                        <Switch />
                    </Form.Item>
                    <Form.Item label="Включить firewall (iptables/nftables)" name="enableFirewall" valuePropName="checked">
                        <Switch />
                    </Form.Item>
                    <Form.Item label="Правила firewall (опционально)" name="firewallRules" tooltip="Одно правило на строку">
                        <Input.TextArea rows={3} placeholder="-A INPUT -p tcp --dport 22 -j ACCEPT" />
                    </Form.Item>
                    <Form.Item label="Включить IP forwarding (маршрутизация)" name="enableIpForward" valuePropName="checked">
                        <Switch />
                    </Form.Item>
                </>
            ),
        },
        {
            key: 'security',
            label: 'Безопасность',
            children: (
                <>
                    <Form.Item label="Режим SELinux" name="selinuxMode">
                        <Select
                            options={[
                                { value: 'enforcing', label: 'Enforcing (включён)' },
                                { value: 'permissive', label: 'Permissive (логирование)' },
                                { value: 'disabled', label: 'Disabled (выключен)' },
                            ]}
                        />
                    </Form.Item>
                    <Form.Item label="Включить auditd" name="auditdEnabled" valuePropName="checked">
                        <Switch />
                    </Form.Item>
                    <Form.Item label="Включить fail2ban" name="fail2banEnabled" valuePropName="checked">
                        <Switch />
                    </Form.Item>
                    <Form.Item label="Максимум попыток входа" name="maxAuthRetries">
                        <InputNumber min={1} max={10} style={{ width: '100%' }} />
                    </Form.Item>
                </>
            ),
        },
        {
            key: 'users',
            label: 'Пользователи',
            children: (
                <>
                    <Form.Item label="Создать дополнительного пользователя" name="createUser" valuePropName="checked">
                        <Switch />
                    </Form.Item>
                    {createUser && (
                        <>
                            <Form.Item label="Имя пользователя" name="newUsername" rules={[{ required: true }]}>
                                <Input />
                            </Form.Item>
                            <Form.Item label="Предоставить sudo-доступ" name="newUserSudo" valuePropName="checked">
                                <Switch />
                            </Form.Item>
                        </>
                    )}
                </>
            ),
        },
        {
            key: 'monitoring',
            label: 'Мониторинг',
            children: (
                <Form.Item label="Установить Node Exporter (Prometheus)" name="installNodeExporter" valuePropName="checked">
                    <Switch />
                </Form.Item>
            ),
        },
    ];

    return (
        <Modal
            title={`Linux config: ${hostname || 'device'}`}
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
                    hostname: '',
                    timezone: 'UTC',
                    ntpServers: '0.pool.ntp.org 1.pool.ntp.org',
                    autoUpgrade: false,
                    upgradePolicy: 'security',
                    dnsServers: '8.8.8.8 8.8.4.4',
                    sshPort: 22,
                    sshPasswordAuth: true,
                    sshRootLogin: false,
                    enableFirewall: true,
                    firewallRules: '',
                    enableIpForward: false,
                    selinuxMode: 'enforcing',
                    auditdEnabled: true,
                    fail2banEnabled: false,
                    maxAuthRetries: 3,
                    createUser: false,
                    newUsername: '',
                    newUserSudo: false,
                    installNodeExporter: false,
                }}
            >
                <Tabs activeKey={activeTab} onChange={setActiveTab} items={tabItems} />
                <Button type="primary" htmlType="submit" loading={submitting} block>
                    Применить
                </Button>
            </Form>
        </Modal>
    );
}
