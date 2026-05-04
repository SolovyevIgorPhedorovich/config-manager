import React, { useState } from "react";
import { Modal, Form, Input, Select, Button, message } from "antd";
import { ScanOption } from "../types"

interface ScanDeviceModalProps {
    open: boolean;
    onScan: (options: Partial<ScanOption>) => Promise<void>;
    onCancel: () => void;
}

export const ScanDeviceModal:
    React.FC<ScanDeviceModalProps> = ({ open, onScan, onCancel }) => {
        const [form] = Form.useForm();
        const [loading, setLoading] =useState(false);
        const [ip, setIP] = useState<string>('');
        const [mask, setMask] = useState<number>(24);
        const [port, setPort] = useState<number>(161);
        const [community, setCommunity] = useState<string>('public');
        const [snmpv, setSNMPVersion] = useState<string> ('v1');

        const communityVersion = [
            {
                value: 0,
                label: "v1"
            },
            {
                value: 1,
                label: "v2c"
            },
            {
                value: 2,
                label: "v3"
            }
        ]

        const handleSubmit = async () => {
            try {
                const values = await form.validateFields();
                setLoading(true);
                await onScan(values);
                message.success('Устройство добавлено');
                form.resetFields();
                onCancel();
            } catch (error) {
                console.error('Ошибка добавления:', error);
                message.error('Не удалось добавить устройство');
            } finally {
                setLoading(false);
            }
        };

        return (
    <Modal
      title="Поиск устройств"
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
          Поиск
        </Button>,
      ]}
    >
      <Form form={form} layout="vertical" initialValues={{ isActive: true }}>
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
            name="mask"
            label="Маска сети">
        </Form.Item>

        <Form.Item
            name="port"
            label="Порт">
        </Form.Item>

        <Form.Item
          name="SNMP community"
          label="SNMP community"
          rules={[{ required: true, message: 'Выберите версию' }]}
        >
          <Select
            options={communityVersion}
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
}