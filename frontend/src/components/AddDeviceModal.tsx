import React, { useState } from "react";
import { Modal, Form, Input, Select, Button, message } from "antd";
import type { Device, DeviceTypeCode } from "../types";

interface AddDeviceModalProps {
    open: boolean;
    onCancel: () => void;
    onAdd: (device: Partial<Device>) => Promise<void>;
}

export const AddDeviceModal:
    React.FC<AddDeviceModalProps> = ({ open, onCancel, onAdd }) => {
        const [form] = Form.useForm();
        const [loading, setLoading] = useState(false);

        const deviceTypes = [
            {
                value: 0,
                label: 'ПК',
                color: "#1890ff"
            },
            {
                value: 1,
                label: 'МФУ',
                color: "#52c41a"
            },
            {
                value: 2,
                label: 'Cisco',
                color: "#fa8b0f"
            },
            {
                value: 3,
                label: 'VM',
                color: "#f53f3f"
            }
        ];

        const handleSubmit = async () => {
        try {
            const values = await form.validateFields();
            setLoading(true);

            const payload = {
                ...values,
                ips: Array.isArray(values.ip) ? values.ip : (values.ip ? [values.ip] : [])
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
        <Button
          key="submit"
          type="primary"
          loading={loading}
          onClick={handleSubmit}
        >
          Сохранить
        </Button>,
      ]}
    >
      <Form form={form} layout="vertical" initialValues={{ isActive: true }}>
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
            {
              pattern: /^(\d{1,3}\.){3}\d{1,3}$/,
              message: 'Некорректный формат IP',
            },
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
            options={deviceTypes}
            showSearch={{ optionFilterProp: 'label' }}
            popupRender={(menu) => (
              <>
                {React.cloneElement(menu as React.ReactElement, {
                  style: { maxHeight: 300, overflowY: 'auto' },
                })}
              </>
            )}
          />
        </Form.Item>

        <Form.Item
          name="groupName"
          label="Группа (например, Офис_А)"
          initialValue="default"
          tooltip="Используется для фильтрации и массовых операций"
        >
          <Input placeholder="Офис_А" />
        </Form.Item>

        <Form.Item
          name="osVersion"
          label="Версия ОС / Модель оборудования"
          tooltip="Необязательно: Windows 10 Pro, Alt Linux 10, Cisco IOS XE и т.д."
        >
          <Input placeholder="например, Windows 10 Enterprise" />
        </Form.Item>

        <Form.Item
          name="isActive"
          label="Активное устройство?"
          valuePropName="checked"
          tooltip="Если отключено — устройство не будет участвовать в автоматических операциях"
        >
          <Select
            options={[
              { value: true, label: 'Да' },
              { value: false, label: 'Нет (скрыто из списков)' }
            ]}
          />
        </Form.Item>
      </Form>

      <div style={{ marginTop: 16, padding: '12px', background: '#f0f5ff', borderRadius: 4 }}>
        <strong>💡 Совет:</strong>
        <p style={{ margin: '8px 0' }}>
          Для Windows-устройств убедитесь, что включён WinRM или SSH-сервис.  
          Для МФУ — разрешите SNMP и REST API.  
          Для Cisco — включите `netconf-yang` и `http/https server`.
        </p>
      </div>
    </Modal>
  );
};