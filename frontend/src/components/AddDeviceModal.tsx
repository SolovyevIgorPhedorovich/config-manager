import React, { useMemo, useState } from "react";
import { Modal, Form, Input, Select, Button, message, Divider, Typography, Alert } from "antd";
 import type { Device, DeviceTypeCode } from "../types";
 
 interface AddDeviceModalProps {
     open: boolean;
     onCancel: () => void;
     onAdd: (device: Partial<Device>) => Promise<void>;
 }
 
type ConnectionMode = 'ssh_linux' | 'ssh_cisco' | 'winrm' | 'snmp';

const connectionByType: Record<DeviceTypeCode, ConnectionMode[]> = {
  0: ['winrm', 'ssh_linux'],
  1: ['snmp'],
  2: ['ssh_cisco', 'snmp'],
  3: ['ssh_linux', 'winrm']
};

const connectionOptions: { value: ConnectionMode; label: string }[] = [
  { value: 'ssh_linux', label: 'SSH Linux' },
  { value: 'ssh_cisco', label: 'SSH Cisco' },
  { value: 'winrm', label: 'WinRM / PowerShell Remoting' },
  { value: 'snmp', label: 'SNMP (принтеры/МФУ)' }
];

export const AddDeviceModal: React.FC<AddDeviceModalProps> = ({ open, onCancel, onAdd }) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const selectedType: DeviceTypeCode = Form.useWatch('type', form) ?? 0;
  const selectedConnection: ConnectionMode | undefined = Form.useWatch('connectionMode', form);

  const availableConnections = useMemo(() => {
    return connectionOptions.filter(option => connectionByType[selectedType]?.includes(option.value));
  }, [selectedType]);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);

      const payload = {
        ...values,
        ips: Array.isArray(values.ip) ? values.ip : (values.ip ? [values.ip] : []),
        connectionProfile: {
          mode: values.connectionMode,
          host: values.ip,
          port: values.port,
          username: values.username,
          authType: values.authType,
          secret: values.secret,
          domain: values.domain,
          community: values.community,
          snmpVersion: values.snmpVersion,
          isHiddenConnection: Boolean(values.isHiddenConnection)
         }
      };

      delete payload.ip;
 
      await onAdd(payload);
      message.success('Устройство добавлено');
      form.resetFields();
      onCancel();
    } catch (error) {
      console.error('Ошибка добавления:', error);
      message.error('Не удалось добавить устройство: ' + String(error));
    } finally {
      setLoading(false);
    }
  };
 
   return (
     <Modal
       title="➕ Добавить новое устройство"
       open={open}
       onCancel={() => { form.resetFields(); onCancel(); }}
       footer={[
         <Button key="cancel" onClick={() => { form.resetFields(); onCancel(); }}>
           Отмена
         </Button>,
        <Button key="submit" type="primary" loading={loading} onClick={handleSubmit}>
           Сохранить
         </Button>,
       ]}
      width={800}
     >
      <Form form={form} layout="vertical" initialValues={{ isActive: true, isHiddenConnection: true, authType: 'password', snmpVersion: 'v2c' }}>
        <Form.Item name="hostname" label="Имя хоста (Hostname)" rules={[{ required: true, message: 'Введите имя устройства' }]}>
           <Input placeholder="например, pc-105" />
         </Form.Item>
 
         <Form.Item
           name="ip"
           label="IP-адрес"
          rules={[{ required: true, message: 'Введите IP-адрес' }, { pattern: /^(\d{1,3}\.){3}\d{1,3}$/, message: 'Некорректный формат IP' }]}
         >
           <Input placeholder="например, 192.168.1.105" />
         </Form.Item>
 
        <Form.Item name="type" label="Тип устройства" rules={[{ required: true, message: 'Выберите тип устройства' }]}>
          <Select options={[{ value: 0, label: 'ПК' }, { value: 1, label: 'МФУ' }, { value: 2, label: 'Cisco' }, { value: 3, label: 'VM' }]} />
         </Form.Item>

        <Form.Item name="connectionMode" label="Способ подключения" rules={[{ required: true, message: 'Выберите способ подключения' }]}>
          <Select options={availableConnections} placeholder="Выберите протокол" />
         </Form.Item>
 
        <Divider>Параметры подключения и скрытого подключения</Divider>

        {(selectedConnection === 'ssh_linux' || selectedConnection === 'ssh_cisco' || selectedConnection === 'winrm') && (
          <>
            <Form.Item name="username" label="Пользователь" rules={[{ required: true, message: 'Введите имя пользователя' }]}>
              <Input placeholder="admin/root/DOMAIN\\user" />
            </Form.Item>
            <Form.Item name="port" label="Порт" rules={[{ required: true, message: 'Укажите порт' }]}>
              <Input placeholder={selectedConnection === 'winrm' ? '5985 или 5986' : '22'} />
            </Form.Item>
            <Form.Item name="authType" label="Тип аутентификации">
              <Select options={[{ value: 'password', label: 'Пароль' }, { value: 'key', label: 'Ключ / сертификат' }]} />
            </Form.Item>
            <Form.Item name="secret" label="Секрет (скрытое подключение)" rules={[{ required: true, message: 'Введите секрет/пароль' }]}>
              <Input.Password placeholder="Пароль или закрытый ключ" visibilityToggle />
            </Form.Item>
          </>
        )}

        {selectedConnection === 'winrm' && (
          <Form.Item name="domain" label="AD-домен / рабочая группа">
            <Input placeholder="example.local" />
          </Form.Item>
        )}

        {selectedConnection === 'snmp' && (
          <>
            <Form.Item name="snmpVersion" label="Версия SNMP" rules={[{ required: true }]}>
              <Select options={[{ value: 'v2c', label: 'SNMP v2c' }, { value: 'v3', label: 'SNMP v3' }]} />
            </Form.Item>
            <Form.Item name="community" label="Community / securityName" rules={[{ required: true, message: 'Заполните community' }]}>
              <Input.Password placeholder="public/private/имя SNMPv3" />
            </Form.Item>
          </>
        )}

        <Form.Item name="isHiddenConnection" label="Скрывать параметры авторизации" tooltip="При включении пароль/ключ маскируется в UI и аудит-логах.">
          <Select options={[{ value: true, label: 'Да, скрывать' }, { value: false, label: 'Нет, показывать оператору' }]} />
         </Form.Item>
 
        <Form.Item name="groupName" label="Группа (например, Офис_А)" initialValue="default">
          <Input placeholder="Офис_А" />
         </Form.Item>
        <Form.Item name="isActive" label="Активное устройство?">
          <Select options={[{ value: true, label: 'Да' }, { value: false, label: 'Нет (скрыто из списков)' }]} />
        </Form.Item>

        <Alert type="info" message={<Typography.Text>Формы редактирования конфигурации автоматически подстраиваются под выбранный протокол: SSH Linux/Cisco, WinRM или SNMP.</Typography.Text>} />
      </Form>
     </Modal>
   );
};
