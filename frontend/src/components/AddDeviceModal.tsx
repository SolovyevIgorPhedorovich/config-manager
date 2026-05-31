import React, { useMemo, useState, useEffect } from "react";
import { Modal, Form, Input, Select, Button, message, Divider, Typography, Alert } from "antd";
import type { Device, DeviceTypeCode } from "../types";

interface AddDeviceModalProps {
    open: boolean;
    onCancel: () => void;
    onAdd: (device: Partial<Device>) => Promise<void>;
}

type ConnectionMode = 'ssh_linux' | 'ssh_cisco' | 'winrm' | 'snmp';
type OSType = 'linux' | 'windows';

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
  const selectedOS: OSType | undefined = Form.useWatch('operatingSystem', form);
  const selectedConnection: ConnectionMode | undefined = Form.useWatch('connectionMode', form);
  const currentPort = Form.useWatch('port', form);
  const selectedManufacturer = Form.useWatch('manufacturer', form);

  // Показывать выбор ОС только для типов "ПК" (0) и "VM" (3)
  const showOSSelector = selectedType === 0 || selectedType === 3;
  
  // Показывать выбор производителя только для МФУ (1)
  const showManufacturerSelector = selectedType === 1;
  
  // Показывать поле модели для всех типов, но особенно важно для МФУ
  const showModelField = true;

  // Доступные протоколы в зависимости от типа устройства
  const availableConnections = useMemo(() => {
    return connectionOptions.filter(option => connectionByType[selectedType]?.includes(option.value));
  }, [selectedType]);

  // Автоматическая подстановка протокола и порта при выборе ОС
  useEffect(() => {
    if (!showOSSelector || !selectedOS) return;

    let newMode: ConnectionMode | undefined;
    let defaultPort: number | undefined;

    if (selectedOS === 'windows') {
      newMode = 'winrm';
      defaultPort = 5986; // WinRM HTTPS
    } else if (selectedOS === 'linux') {
      newMode = 'ssh_linux';
      defaultPort = 22;
    }

    if (newMode && selectedConnection !== newMode) {
      form.setFieldValue('connectionMode', newMode);
    }

    // Устанавливаем порт по умолчанию, только если текущий порт не задан или совпадает со старым дефолтным значением
    const isPortEmpty = !currentPort || currentPort === '';
    const isOldDefaultPort = currentPort === 22 || currentPort === 5986;
    if ((isPortEmpty || isOldDefaultPort) && defaultPort) {
      form.setFieldValue('port', defaultPort);
    }
  }, [selectedOS, showOSSelector, form, selectedConnection, currentPort]);

  // Сброс ОС и протокола при смене типа устройства
  useEffect(() => {
    if (!showOSSelector) {
      form.setFieldValue('operatingSystem', undefined);
    }
    if (!showManufacturerSelector) {
      form.setFieldValue('manufacturer', undefined);
    }
  }, [selectedType, showOSSelector, showManufacturerSelector, form]);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);

      // Базовый payload
      const payload: any = {
        hostname: values.hostname,
        ips: Array.isArray(values.ip) ? values.ip : (values.ip ? [values.ip] : []),
        type: values.type,
        groupName: values.groupName || 'default',
        isActive: values.isActive !== undefined ? values.isActive : true,
      };

      // Добавляем ОС только для ПК и VM
      if ((values.type === 0 || values.type === 3) && values.operatingSystem) {
        payload.operatingSystem = values.operatingSystem;
      }

      // Добавляем производителя только для МФУ
      if (values.type === 1 && values.manufacturer) {
        payload.manufacturer = values.manufacturer;
      }

      // Добавляем модель если указана
      if (values.model) {
        payload.model = values.model;
      }

      // Добавляем профиль подключения
      payload.connectionProfile = {
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
      };

      console.log('Отправляемые данные:', payload); // Для отладки
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
      <Form form={form} layout="vertical" initialValues={{ isActive: true, isHiddenConnection: true, authType: 'password', snmpVersion: 'v2c', groupName: 'default' }}>
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

        {/* Выбор ОС - отображается только для ПК и VM */}
        {showOSSelector && (
          <Form.Item 
            name="operatingSystem" 
            label="Операционная система" 
            rules={[{ required: true, message: 'Выберите ОС устройства' }]}
            tooltip="Выбор ОС автоматически подставит протокол подключения и порт по умолчанию"
          >
            <Select 
              placeholder="Выберите ОС"
              options={[
                { value: 'linux', label: 'Linux / Unix' },
                { value: 'windows', label: 'Windows' }
              ]}
            />
          </Form.Item>
        )}

        {/* Выбор производителя - отображается только для МФУ */}
        {showManufacturerSelector && (
          <Form.Item 
            name="manufacturer" 
            label="Производитель МФУ" 
            rules={[{ required: true, message: 'Выберите производителя' }]}
          >
            <Select
              placeholder="Выберите производителя"
              options={[
                { value: 'HP', label: 'HP' },
                { value: 'Canon', label: 'Canon' },
                { value: 'Xerox', label: 'Xerox' },
                { value: 'Kyocera', label: 'Kyocera' },
                { value: 'Brother', label: 'Brother' },
                { value: 'Epson', label: 'Epson' },
                { value: 'Samsung', label: 'Samsung' },
                { value: 'Ricoh', label: 'Ricoh' },
                { value: 'Sharp', label: 'Sharp' },
                { value: 'Konica Minolta', label: 'Konica Minolta' }
              ]}
              showSearch
              filterOption={(input, option) => 
                (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
              }
            />
          </Form.Item>
        )}

        {/* Поле модели - для всех типов устройств */}
        <Form.Item 
          name="model" 
          label="Модель устройства"
          tooltip="Укажите модель устройства для более точной идентификации"
        >
          <Input placeholder="например, OptiPlex 7090, LaserJet Pro M428fdw, C9300" />
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

        <Form.Item name="groupName" label="Группа (например, Офис_А)" initialValue="default">
          <Input placeholder="Офис_А" />
        </Form.Item>
        
        <Form.Item name="isActive" label="Активное устройство?">
          <Select options={[{ value: true, label: 'Да' }, { value: false, label: 'Нет (скрыто из списков)' }]} />
        </Form.Item>

        <Alert type="info" message={
          <Typography.Text>
            • При выборе ОС автоматически подставляется протокол и порт по умолчанию (Linux: SSH/22, Windows: WinRM/5986).<br />
            • Вы можете вручную изменить протокол и порт при необходимости.<br />
            • Для МФУ обязательно укажите производителя.
          </Typography.Text>
        } />
      </Form>
    </Modal>
  );
};