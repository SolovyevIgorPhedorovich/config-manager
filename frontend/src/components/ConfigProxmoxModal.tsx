import React, { useState } from 'react';
import {
    Modal,
    Form,
    Input,
    InputNumber,
    Button,
    Select,
    Switch,
    message,
    Tabs,
    Divider,
    Space,
    Radio,
} from 'antd';

interface Props {
    open: boolean;
    onClose: () => void;
    hostname?: string;
    deviceId?: number;
}

export default function ConfigProxmoxModal({ open, onClose, hostname, deviceId }: Props) {
    const [form] = Form.useForm();
    const [submitting, setSubmitting] = useState(false);
    const storageType = Form.useWatch('storageType', form);
    const vmType = Form.useWatch('vmType', form);

    const onFinish = async (values: Record<string, any>) => {
        if (!deviceId) return message.error('Не выбрано устройство');
        setSubmitting(true);
        try {
            await fetch('/api/devices/configure', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ deviceId, channel: 'ssh', target: 'proxmox', params: values }),
            });
            message.success('Настройки Proxmox отправлены');
            form.resetFields();
            onClose();
        } catch {
            message.error('Ошибка отправки настроек Proxmox');
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Modal
            title={`Proxmox настройки: ${hostname || 'device'}`}
            open={open}
            onCancel={onClose}
            footer={null}
            width={750}
            destroyOnClose
        >
            <Form
                form={form}
                layout="vertical"
                onFinish={onFinish}
                initialValues={{
                    // Подключение
                    apiEndpoint: 'https://192.168.1.100:8006/api2/json',
                    apiToken: '',
                    insecure: false,
                    // Datacenter
                    clusterName: '',
                    httpProxy: '',
                    language: 'en',
                    keyboard: 'en-us',
                    consoleViewer: 'html5',
                    haEnabled: false,
                    haShutdownPolicy: 'conditional',
                    // Хранилище
                    storageType: 'dir',
                    storageName: 'local-new',
                    storagePath: '/var/lib/vz/new',
                    storageServer: '',
                    storageExport: '',
                    storageContent: ['images', 'iso'],
                    pruneKeepLast: 3,
                    // Пользователи
                    userName: '',
                    userEmail: '',
                    userGroup: '',
                    userEnabled: true,
                    userExpire: '',
                    userComment: '',
                    // VM / CT
                    vmId: 100,
                    vmName: 'test-vm',
                    vmNode: 'pve',
                    vmBios: 'seabios',
                    vmMemory: 2048,
                    vmBalloon: 0,
                    vmCores: 2,
                    vmSockets: 1,
                    vmNetBridge: 'vmbr0',
                    vmNetModel: 'virtio',
                    vmDiskSize: 32,
                    vmDiskStorage: 'local-lvm',
                    ctUnprivileged: true,
                    ctTemplate: 'debian-12-standard_12.2-1_amd64.tar.zst',
                    ctStorage: 'local',
                    ctMemory: 1024,
                    ctCores: 1,
                    ctSwap: 512,
                }}
            >
                <Tabs defaultActiveKey="connect">
                    <Tabs.TabPane tab="🔌 Подключение" key="connect">
                        <Form.Item label="API Endpoint" name="apiEndpoint" rules={[{ required: true, type: 'url' }]}>
                            <Input placeholder="https://pve.domain.com:8006/api2/json" />
                        </Form.Item>
                        <Form.Item label="API Token" name="apiToken" tooltip="Формат: USER@REALM!TOKENID=SECRET">
                            <Input.Password placeholder="root@pam!monitoring=xxxxxxxx-xxxx-xxxx" />
                        </Form.Item>
                        <Form.Item label="Пропустить проверку SSL" name="insecure" valuePropName="checked">
                            <Switch />
                        </Form.Item>
                    </Tabs.TabPane>

                    <Tabs.TabPane tab="🏢 Datacenter / Кластер" key="cluster">
                        <Form.Item label="Имя кластера" name="clusterName" tooltip="Уникальное имя, нельзя изменить позже">
                            <Input placeholder="my-cluster" />
                        </Form.Item>
                        <Form.Item label="HTTP Proxy" name="httpProxy">
                            <Input placeholder="http://proxy.local:3128" />
                        </Form.Item>
                        <Space wrap>
                            <Form.Item label="Язык веб-интерфейса" name="language">
                                <Select style={{ width: 140 }} options={[{ value: 'en', label: 'English' }, { value: 'ru', label: 'Русский' }, { value: 'de', label: 'Deutsch' }]} />
                            </Form.Item>
                            <Form.Item label="Раскладка клавиатуры" name="keyboard">
                                <Select style={{ width: 140 }} options={[{ value: 'en-us', label: 'US' }, { value: 'ru', label: 'Russian' }]} />
                            </Form.Item>
                            <Form.Item label="Консоль VM" name="consoleViewer">
                                <Radio.Group>
                                    <Radio value="html5">HTML5 (noVNC)</Radio>
                                    <Radio value="vv">SPICE (virt-viewer)</Radio>
                                    <Radio value="xtermjs">xterm.js</Radio>
                                </Radio.Group>
                            </Form.Item>
                        </Space>

                        <Divider orientation="vertical">Высокая доступность (HA)</Divider>
                        <Form.Item label="Включить HA" name="haEnabled" valuePropName="checked">
                            <Switch />
                        </Form.Item>
                        {Form.useWatch('haEnabled', form) && (
                            <Form.Item label="Политика выключения" name="haShutdownPolicy" tooltip="conditional — выключение только если возможно, freeze — остановка в текущем состоянии">
                                <Select options={[{ value: 'conditional', label: 'Conditional' }, { value: 'freeze', label: 'Freeze' }]} />
                            </Form.Item>
                        )}
                    </Tabs.TabPane>

                    <Tabs.TabPane tab="💾 Хранилище" key="storage">
                        <Form.Item label="Тип хранилища" name="storageType">
                            <Select options={[{ value: 'dir', label: 'Directory' }, { value: 'nfs', label: 'NFS' }, { value: 'lvmthin', label: 'LVM-thin' }, { value: 'zfspool', label: 'ZFS' }]} />
                        </Form.Item>
                        <Form.Item label="Имя хранилища" name="storageName" rules={[{ required: true }]}>
                            <Input placeholder="local-new" />
                        </Form.Item>

                        {storageType === 'dir' && (
                            <Form.Item label="Путь на диске" name="storagePath" rules={[{ required: true }]}>
                                <Input placeholder="/var/lib/vz/new" />
                            </Form.Item>
                        )}

                        {storageType === 'nfs' && (
                            <>
                                <Form.Item label="NFS сервер" name="storageServer" rules={[{ required: true }]}>
                                    <Input placeholder="192.168.1.100" />
                                </Form.Item>
                                <Form.Item label="NFS экспорт" name="storageExport" rules={[{ required: true }]}>
                                    <Input placeholder="/volume1/backup" />
                                </Form.Item>
                            </>
                        )}

                        <Form.Item label="Типы контента" name="storageContent">
                            <Select mode="multiple" options={[{ value: 'images', label: 'Образы VM/CT' }, { value: 'iso', label: 'ISO образы' }, { value: 'backup', label: 'Резервные копии' }, { value: 'vztmpl', label: 'Шаблоны контейнеров' }]} />
                        </Form.Item>

                        <Form.Item label="Политика очистки бэкапов" name="pruneKeepLast">
                            <InputNumber min={1} max={365} style={{ width: '100%' }} />
                        </Form.Item>
                    </Tabs.TabPane>

                    <Tabs.TabPane tab="👥 Пользователи" key="users">
                        <Form.Item label="Имя пользователя (userid@realm)" name="userName" rules={[{ required: true }]}>
                            <Input placeholder="john@pve" />
                        </Form.Item>
                        <Form.Item label="Email" name="userEmail">
                            <Input type="email" placeholder="admin@example.com" />
                        </Form.Item>
                        <Form.Item label="Группа" name="userGroup">
                            <Input placeholder="groupname" />
                        </Form.Item>
                        <Space>
                            <Form.Item label="Включён" name="userEnabled" valuePropName="checked">
                                <Switch />
                            </Form.Item>
                            <Form.Item label="Срок действия" name="userExpire">
                                <Input type="date" style={{ width: 160 }} />
                            </Form.Item>
                        </Space>
                        <Form.Item label="Пароль" name="userPassword">
                            <Input.Password placeholder="••••••" />
                        </Form.Item>
                    </Tabs.TabPane>

                    <Tabs.TabPane tab="🖥️ Виртуализация" key="virtualization">
                        <Form.Item label="Тип гостя" name="vmType" initialValue="qemu">
                            <Radio.Group>
                                <Radio.Button value="qemu">QEMU (KVM) VM</Radio.Button>
                                <Radio.Button value="lxc">LXC Container</Radio.Button>
                            </Radio.Group>
                        </Form.Item>

                        {vmType === 'qemu' && (
                            <>
                                <Form.Item label="VM ID" name="vmId" rules={[{ required: true }]}>
                                    <InputNumber min={100} max={999999999} style={{ width: '100%' }} />
                                </Form.Item>
                                <Form.Item label="Имя VM" name="vmName" rules={[{ required: true }]}>
                                    <Input placeholder="my-vm" />
                                </Form.Item>
                                <Form.Item label="Целевой узел" name="vmNode">
                                    <Input placeholder="pve" />
                                </Form.Item>
                                <Form.Item label="BIOS" name="vmBios">
                                    <Select options={[{ value: 'seabios', label: 'SeaBIOS' }, { value: 'ovmf', label: 'OVMF (UEFI)' }]} />
                                </Form.Item>
                                <Form.Item label="Память (MB)" name="vmMemory" rules={[{ required: true }]}>
                                    <InputNumber min={512} step={1024} style={{ width: '100%' }} />
                                </Form.Item>
                                <Form.Item label="Ballooning (MB, 0 = выкл)" name="vmBalloon">
                                    <InputNumber min={0} step={256} style={{ width: '100%' }} />
                                </Form.Item>
                                <Form.Item label="CPU (ядра)" name="vmCores" rules={[{ required: true }]}>
                                    <InputNumber min={1} max={64} style={{ width: '100%' }} />
                                </Form.Item>
                                <Form.Item label="CPU (сокеты)" name="vmSockets">
                                    <InputNumber min={1} max={4} style={{ width: '100%' }} />
                                </Form.Item>
                                <Divider orientation="vertical">Сеть</Divider>
                                <Form.Item label="Мост" name="vmNetBridge">
                                    <Select options={[{ value: 'vmbr0' }, { value: 'vmbr1' }]} />
                                </Form.Item>
                                <Form.Item label="Модель" name="vmNetModel">
                                    <Select options={[{ value: 'virtio', label: 'VirtIO (рекомендуется)' }, { value: 'e1000', label: 'Intel E1000' }, { value: 'rtl8139', label: 'Realtek 8139' }]} />
                                </Form.Item>
                                <Divider orientation="vertical">Диск</Divider>
                                <Form.Item label="Размер (GB)" name="vmDiskSize" rules={[{ required: true }]}>
                                    <InputNumber min={1} max={1000} style={{ width: '100%' }} />
                                </Form.Item>
                                <Form.Item label="Хранилище" name="vmDiskStorage">
                                    <Input placeholder="local-lvm" />
                                </Form.Item>
                            </>
                        )}

                        {vmType === 'lxc' && (
                            <>
                                <Form.Item label="CT ID" name="vmId" rules={[{ required: true }]}>
                                    <InputNumber min={100} style={{ width: '100%' }} />
                                </Form.Item>
                                <Form.Item label="Имя контейнера" name="vmName" rules={[{ required: true }]}>
                                    <Input placeholder="my-container" />
                                </Form.Item>
                                <Form.Item label="Непривилегированный" name="ctUnprivileged" valuePropName="checked">
                                    <Switch />
                                </Form.Item>
                                <Form.Item label="Шаблон ОС" name="ctTemplate" rules={[{ required: true }]}>
                                    <Select options={[{ value: 'debian-12-standard_12.2-1_amd64.tar.zst', label: 'Debian 12' }, { value: 'ubuntu-22.04-standard_22.04-1_amd64.tar.zst', label: 'Ubuntu 22.04' }, { value: 'alpine-3.18-default_20231026_amd64.tar.xz', label: 'Alpine 3.18' }]} />
                                </Form.Item>
                                <Form.Item label="Хранилище шаблона" name="ctStorage">
                                    <Input placeholder="local" />
                                </Form.Item>
                                <Form.Item label="Память (MB)" name="ctMemory" rules={[{ required: true }]}>
                                    <InputNumber min={128} step={512} style={{ width: '100%' }} />
                                </Form.Item>
                                <Form.Item label="Swap (MB)" name="ctSwap">
                                    <InputNumber min={0} step={256} style={{ width: '100%' }} />
                                </Form.Item>
                                <Form.Item label="CPU (ядра)" name="ctCores" rules={[{ required: true }]}>
                                    <InputNumber min={1} max={16} style={{ width: '100%' }} />
                                </Form.Item>
                            </>
                        )}
                    </Tabs.TabPane>
                </Tabs>

                <Form.Item>
                    <Button type="primary" htmlType="submit" loading={submitting} block>
                        Применить
                    </Button>
                </Form.Item>
            </Form>
        </Modal>
    );
}