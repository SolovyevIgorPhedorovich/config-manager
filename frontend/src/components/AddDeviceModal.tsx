import React, { useState } from "react";
import { Modal, Form, Input, Select, Button, message, Typography, Alert } from "antd";
import type { Device, DeviceTypeCode } from "../types";

interface AddDeviceModalProps {
    open: boolean;
    onCancel: () => void;
    onAdd: (device: Partial<Device>) => Promise<void>;
}

type OSType = 'linux' | 'windows';

export const AddDeviceModal: React.FC<AddDeviceModalProps> = ({ open, onCancel, onAdd }) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const selectedType: DeviceTypeCode = Form.useWatch('type', form) ?? 0;
  
  // Показывать выбор ОС только для типов "ПК" (0) и "VM" (3)
  const showOSSelector = selectedType === 0 || selectedType === 3;
  
  // Показывать выбор производителя только для МФУ (1)
  const showManufacturerSelector = selectedType === 1;

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);

      const payload: any = {
        hostname: values.hostname,
        ips: Array.isArray(values.ip) ? values.ip : (values.ip ? [values.ip] : []),
        type: values.type,
        groupName: values.groupName || 'default',
        isActive: values.isActive !== undefined ? values.isActive : true,
      };

      if ((values.type === 0  || values.type === 3) && values.operatingSystem) {
        payload.operatingSystem = values.operatingSystem;
      }

      if (values.type === 1 && values.manufacturer) {
        payload.manufacturer = values.manufacturer;
      }

      if (values.model) {
        payload.model = values.model;
      }

      console.log('Отправляемые данные:', payload);
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
      width={600}
    >
      <Form form={form} layout="vertical" initialValues={{ isActive: true, groupName: 'default' }}>
        <Form.Item 
          name="hostname" 
          label="Имя хоста (Hostname)" 
          rules={[{ required: true, message: 'Введите имя устройства' }]}
        >
          <Input placeholder="например, pc-105" />
        </Form.Item>

        <Form.Item
          name="ip"
          label="IP-адрес"
          rules={[
            { required: true, message: 'Введите IP-адрес' }, 
            { pattern: /^(\d{1,3}\.){3}\d{1,3}$/, message: 'Некорректный формат IP' }
          ]}
        >
          <Input placeholder="например, 192.168.1.105" />
        </Form.Item>

        <Form.Item 
          name="type" 
          label="Тип устройства" 
          rules={[{ required: true, message: 'Выберите тип устройства' }]}
        >
          <Select 
            options={[
              { value: 0, label: 'ПК' }, 
              { value: 1, label: 'МФУ' }, 
              { value: 2, label: 'Cisco' }, 
              { value: 3, label: 'VM' }
            ]} 
          />
        </Form.Item>
        
        {showOSSelector && (
          <Form.Item 
            name="operatingSystem" 
            label="Операционная система" 
            rules={[{ required: true, message: 'Выберите ОС устройства' }]}
            tooltip="Укажите операционную систему устройства"
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

        <Form.Item 
          name="groupName" 
          label="Группа (например, Офис_А)" 
          initialValue="default"
        >
          <Input placeholder="Офис_А" />
        </Form.Item>
        
        <Form.Item 
          name="isActive" 
          label="Активное устройство?"
        >
          <Select 
            options={[
              { value: true, label: 'Да' }, 
              { value: false, label: 'Нет (скрыто из списков)' }
            ]} 
          />
        </Form.Item>

        <Alert 
          type="info" 
          message={
            <Typography.Text>
              • Для ПК и VM обязательно укажите операционную систему.<br />
              • Для МФУ обязательно укажите производителя.<br />
              • Модель устройства поможет в дальнейшей идентификации.
            </Typography.Text>
          } 
        />
      </Form>
    </Modal>
  );
};