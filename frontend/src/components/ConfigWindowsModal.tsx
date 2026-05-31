import React from 'react';
import { Modal, Form, InputNumber, Button, Select, Switch, message, Input } from 'antd';

interface Props {
  open: boolean;
  onClose: () => void;
  hostname?: string;
  deviceId?: number;
}

export default function ConfigWindowsModal({ open, onClose, hostname, deviceId }: Props) {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = React.useState(false);
  const changeComputerName = Form.useWatch('changeComputerName', form);

  const onFinish = async (values: any) => {
    if (!deviceId) return message.error('Не выбрано устройство');
    setSubmitting(true);
    try {
      // Формируем тело запроса согласно ApplyConfigRequest
      const payload = {
        deviceIds: [deviceId],                           // массив устройств
        configData: values,                               // все параметры конфигурации
        deviceCredentials: {                             // учётные данные для WinRM
          [deviceId]: {
            username: values.winrmUsername,
            password: values.winrmPassword,
            port: values.winrmPort || 5985
          }
        }
      };
      // Удаляем поля учётных данных из configData, чтобы они не попали в параметры конфигурации
      delete payload.configData.winrmUsername;
      delete payload.configData.winrmPassword;
      delete payload.configData.winrmPort;

      const response = await fetch('/api/v1/devices/configure', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      if (!response.ok) throw new Error(await response.text());
      const result = await response.json();
      message.success('Windows-конфигурация отправлена. Задача: ' + result.taskGroupIds[0]);
      form.resetFields();
      onClose();
    } catch (err: any) {
      message.error(`Ошибка: ${err.message}`);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal title={`Windows config: ${hostname || 'device'}`} open={open} onCancel={onClose} footer={null} width={650} destroyOnClose>
      <Form form={form} layout="vertical" onFinish={onFinish} initialValues={{
        timezone: 'UTC',
        ntpServer: 'time.windows.com',
        rdpEnabled: false,
        firewallEnabled: true,
        firewallProfile: 'Public',
        changeComputerName: false,
        newComputerName: '',
        autoLogonEnabled: false,
        autoLogonUser: '',
        autoLogonPassword: '',
        executionPolicy: 'RemoteSigned',
        inactivityTimeout: 15,
        winrmPort: 5985,
      }}>
        {/* WinRM учётные данные */}
        <div style={{ background: '#f5f5f5', padding: 12, borderRadius: 6, marginBottom: 16 }}>
          <strong style={{ display: 'block', marginBottom: 12 }}>Учётные данные WinRM (обязательны)</strong>
          <Form.Item label="Пользователь" name="winrmUsername" rules={[{ required: true, message: 'Введите имя пользователя' }]}>
            <Input placeholder=".\Administrator или domain\user" />
          </Form.Item>
          <Form.Item label="Пароль" name="winrmPassword" rules={[{ required: true, message: 'Введите пароль' }]}>
            <Input.Password placeholder="Пароль для WinRM" />
          </Form.Item>
          <Form.Item label="Порт" name="winrmPort" tooltip="5985 – HTTP, 5986 – HTTPS">
            <InputNumber min={1} max={65535} style={{ width: '100%' }} />
          </Form.Item>
        </div>

        <Form.Item label="Сменить имя компьютера" name="changeComputerName" valuePropName="checked">
          <Switch />
        </Form.Item>
        {changeComputerName && (
          <Form.Item label="Новое имя" name="newComputerName" rules={[{ required: true }, { pattern: /^[a-zA-Z0-9-]+$/, message: 'Только буквы, цифры, дефис' }]}>
            <Input />
          </Form.Item>
        )}

        <Form.Item label="Часовой пояс" name="timezone" rules={[{ required: true }]}>
          <Select options={[
            { value: 'UTC', label: 'UTC' },
            { value: 'Russian Standard Time', label: 'Москва (UTC+3)' },
            { value: 'Eastern Standard Time', label: 'Нью-Йорк (UTC-5)' },
          ]} />
        </Form.Item>

        <Form.Item label="NTP-сервер" name="ntpServer" rules={[{ required: true }]}>
          <Input />
        </Form.Item>

        <Form.Item label="Разрешить RDP" name="rdpEnabled" valuePropName="checked">
          <Switch />
        </Form.Item>

        <Form.Item label="Брандмауэр" name="firewallEnabled" valuePropName="checked">
          <Switch />
        </Form.Item>

        <Form.Item label="Профиль брандмауэра" name="firewallProfile">
          <Select options={[
            { value: 'Domain', label: 'Доменный' },
            { value: 'Private', label: 'Частный' },
            { value: 'Public', label: 'Общедоступный' },
          ]} />
        </Form.Item>

        <Form.Item label="Политика выполнения PowerShell" name="executionPolicy">
          <Select options={[
            { value: 'Restricted', label: 'Restricted' },
            { value: 'AllSigned', label: 'AllSigned' },
            { value: 'RemoteSigned', label: 'RemoteSigned' },
          ]} />
        </Form.Item>

        <Form.Item label="Автовход" name="autoLogonEnabled" valuePropName="checked">
          <Switch />
        </Form.Item>
        {Form.useWatch('autoLogonEnabled', form) && (
          <>
            <Form.Item label="Пользователь" name="autoLogonUser" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item label="Пароль" name="autoLogonPassword" rules={[{ required: true }]}>
              <Input.Password />
            </Form.Item>
          </>
        )}

        <Form.Item label="Таймаут блокировки (мин)" name="inactivityTimeout">
          <InputNumber min={0} style={{ width: '100%' }} />
        </Form.Item>

        <Button type="primary" htmlType="submit" loading={submitting} block>
          Применить через WinRM
        </Button>
      </Form>
    </Modal>
  );
}