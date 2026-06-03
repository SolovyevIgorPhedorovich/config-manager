import React, { useState } from 'react';
import {
  Modal, Form, Input, InputNumber, Select, Switch, Button, Tabs,
  Divider, Space, Alert, Typography, message,
} from 'antd';
import { ThunderboltOutlined } from '@ant-design/icons';
import { configApi } from '../api/configApi';
import SaveToMemoryToggle from './SaveToMemoryToggle';

const { Text } = Typography;

interface Props { open: boolean; onClose: () => void; hostname?: string; deviceId?: number }

const BRANDS = [
  { value: 'kyocera', label: 'Kyocera' },
  { value: 'canon',   label: 'Canon' },
  { value: 'ricoh',   label: 'Ricoh' },
  { value: 'hp',      label: 'HP' },
  { value: 'xerox',   label: 'Xerox' },
  { value: 'samsung', label: 'Samsung' },
  { value: 'brother', label: 'Brother' },
  { value: 'epson',   label: 'Epson' },
];

export default function ConfigMFUModal({ open, onClose, hostname, deviceId }: Props) {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [saveToMemory, setSaveToMemory] = useState(true);

  const snmpEnabled  = Form.useWatch('snmpEnabled',  form);
  const emailAlerts  = Form.useWatch('emailAlerts',  form);
  const scanToFolder = Form.useWatch('scanToFolder', form);
  const scanToEmail  = Form.useWatch('scanToEmail',  form);

  const handleSubmit = async () => {
    if (!deviceId) return message.error('Устройство не выбрано');
    let fields: Record<string, any>;
    try { fields = await form.validateFields(); } catch { return; }

    const configData = { ...fields, saveToMemory };

    setSubmitting(true);
    try {
      await configApi.applyConfig({
        deviceIds:   [deviceId],
        configData,
        credentials: {},   // МФУ конфигурируется через SNMP SET с management-сервера
      });
      message.success(
        saveToMemory
          ? 'SNMP-конфигурация отправлена. Настройки будут сохранены в NVRAM МФУ.'
          : 'SNMP-конфигурация отправлена (только в RAM, сбросится при выключении).'
      );
      onClose();
    } catch (e: any) {
      message.error(e?.response?.data?.message || 'Ошибка');
    } finally {
      setSubmitting(false);
    }
  };

  const tabItems = [
    {
      key: 'basic', label: 'Печать',
      children: (
        <>
          <Form.Item label="Бренд устройства" name="brand" initialValue="kyocera" rules={[{ required: true }]}>
            <Select options={BRANDS} style={{ maxWidth: 200 }} />
          </Form.Item>

          <Divider orientationMargin={0}>Бумага и качество</Divider>
          <Space wrap>
            <Form.Item label="Формат бумаги" name="paperSize" initialValue="A4" style={{ width: 130 }}>
              <Select options={['A4','A3','A5','Letter','Legal'].map(v => ({ value: v, label: v }))} />
            </Form.Item>
            <Form.Item label="Тип бумаги" name="paperType" initialValue="plain" style={{ width: 160 }}>
              <Select options={[
                { value: 'plain',    label: 'Обычная' },
                { value: 'thick',    label: 'Плотная (>90г)' },
                { value: 'envelope', label: 'Конверт' },
                { value: 'recycled', label: 'Переработанная' },
                { value: 'color',    label: 'Цветная' },
              ]} />
            </Form.Item>
            <Form.Item label="Лоток подачи" name="trayMode" initialValue="auto" style={{ width: 170 }}>
              <Select options={[
                { value: 'auto',   label: 'Авто' },
                { value: 'tray1',  label: 'Лоток 1' },
                { value: 'tray2',  label: 'Лоток 2' },
                { value: 'tray3',  label: 'Лоток 3' },
                { value: 'mpTray', label: 'Многоцелевой' },
              ]} />
            </Form.Item>
            <Form.Item label="Разрешение" name="resolution" initialValue="600dpi" style={{ width: 160 }}>
              <Select options={['300dpi','600dpi','1200dpi'].map(v => ({ value: v, label: v }))} />
            </Form.Item>
          </Space>

          <Space wrap>
            <Form.Item label="Двусторонняя печать" name="duplex" initialValue="long-edge" style={{ width: 200 }}>
              <Select options={[
                { value: 'off',        label: 'Отключена' },
                { value: 'long-edge',  label: 'По длинной стороне' },
                { value: 'short-edge', label: 'По короткой стороне' },
              ]} />
            </Form.Item>
            <Form.Item label="Копий по умолчанию" name="copies" initialValue={1} style={{ width: 160 }}>
              <InputNumber min={1} max={999} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="Экономия тонера (EcoPrint)" name="tonerSave" valuePropName="checked" initialValue={false}>
              <Switch />
            </Form.Item>
          </Space>

          <Divider orientationMargin={0}>Энергосбережение</Divider>
          <Space wrap>
            <Form.Item label="Sleep Mode (мин)" name="powerSaveMinutes" initialValue={10} style={{ width: 170 }}
              tooltip="Kyocera: Sleep Mode / Samsung: Power Save">
              <InputNumber min={1} max={240} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="Auto Power Off (мин, 0=выкл)" name="autoPowerOff" initialValue={0} style={{ width: 200 }}>
              <InputNumber min={0} max={480} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
        </>
      ),
    },
    {
      key: 'network', label: 'Сеть и SNMP',
      children: (
        <>
          <Divider orientationMargin={0}>Идентификация устройства (MIB-II)</Divider>
          <Form.Item label="Имя устройства (sysName)" name="deviceName"
            tooltip="OID: SNMPv2-MIB::sysName.0">
            <Input placeholder="MFU-FLOOR3-001" style={{ maxWidth: 300 }} />
          </Form.Item>
          <Form.Item label="Местоположение (sysLocation)" name="location" rules={[{ required: true }]}
            tooltip="OID: SNMPv2-MIB::sysLocation.0">
            <Input placeholder="3 этаж, кабинет 301" />
          </Form.Item>
          <Form.Item label="Контакт (sysContact)" name="contact"
            tooltip="OID: SNMPv2-MIB::sysContact.0">
            <Input placeholder="it-helpdesk@company.com" />
          </Form.Item>

          <Divider orientationMargin={0}>SNMP</Divider>
          <Form.Item label="SNMP-адрес МФУ (для SET-команд)" name="snmpHost">
            <Input placeholder="192.168.1.50" style={{ maxWidth: 240 }} />
          </Form.Item>
          <Form.Item label="Включить SNMP" name="snmpEnabled" valuePropName="checked" initialValue={true}>
            <Switch />
          </Form.Item>
          {snmpEnabled && (
            <Space wrap>
              <Form.Item label="Версия SNMP" name="snmpVersion" initialValue="2c" style={{ width: 130 }}>
                <Select options={['1','2c','3'].map(v => ({ value: v, label: `v${v}` }))} />
              </Form.Item>
              <Form.Item label="Community Read (RO)" name="communityRead" initialValue="public" style={{ minWidth: 180 }}>
                <Input.Password placeholder="public" />
              </Form.Item>
              <Form.Item label="Community Write (RW)" name="communityWrite" initialValue="private" style={{ minWidth: 180 }}>
                <Input.Password placeholder="private" />
              </Form.Item>
            </Space>
          )}
        </>
      ),
    },
    {
      key: 'scan', label: 'Сканирование',
      children: (
        <>
          <Form.Item label="Сканирование в папку (SMB)" name="scanToFolder" valuePropName="checked" initialValue={false}>
            <Switch />
          </Form.Item>
          {scanToFolder && (
            <Space wrap>
              <Form.Item label="SMB-путь" name="scanFolderPath" rules={[{ required: true }]} style={{ minWidth: 260 }}>
                <Input placeholder="\\\\server\\shared\\scans" />
              </Form.Item>
              <Form.Item label="Пользователь SMB" name="scanFolderUser" style={{ minWidth: 180 }}>
                <Input placeholder="domain\\user" />
              </Form.Item>
              <Form.Item label="Пароль SMB" name="scanFolderPassword" style={{ minWidth: 180 }}>
                <Input.Password />
              </Form.Item>
            </Space>
          )}

          <Form.Item label="Сканирование в Email (SMTP)" name="scanToEmail" valuePropName="checked" initialValue={false}>
            <Switch />
          </Form.Item>
          {scanToEmail && (
            <Space wrap>
              <Form.Item label="SMTP-сервер" name="smtpServer" rules={[{ required: true }]} style={{ minWidth: 220 }}>
                <Input placeholder="mail.company.com" />
              </Form.Item>
              <Form.Item label="SMTP-порт" name="smtpPort" initialValue={25} style={{ width: 130 }}>
                <InputNumber min={1} max={65535} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item label="От (From)" name="smtpFrom" style={{ minWidth: 220 }}>
                <Input placeholder="mfu@company.com" />
              </Form.Item>
              <Form.Item label="SMTP-логин" name="smtpUser" style={{ minWidth: 180 }}>
                <Input />
              </Form.Item>
              <Form.Item label="SMTP-пароль" name="smtpPassword" style={{ minWidth: 180 }}>
                <Input.Password />
              </Form.Item>
            </Space>
          )}

          <Divider orientationMargin={0}>Параметры сканирования</Divider>
          <Space wrap>
            <Form.Item label="Формат файла" name="scanFormat" initialValue="PDF" style={{ width: 130 }}>
              <Select options={['PDF','TIFF','JPEG','PNG','DOCX'].map(v => ({ value: v, label: v }))} />
            </Form.Item>
            <Form.Item label="Разрешение сканирования" name="scanResolution" initialValue="300dpi" style={{ width: 170 }}>
              <Select options={['150dpi','200dpi','300dpi','400dpi','600dpi'].map(v => ({ value: v, label: v }))} />
            </Form.Item>
            <Form.Item label="Двустороннее сканирование" name="scanDuplex" valuePropName="checked" initialValue={false}>
              <Switch />
            </Form.Item>
          </Space>
        </>
      ),
    },
    {
      key: 'alerts', label: 'Оповещения',
      children: (
        <>
          <Form.Item label="Email-оповещения о событиях" name="emailAlerts" valuePropName="checked" initialValue={false}>
            <Switch />
          </Form.Item>
          {emailAlerts && (
            <Form.Item label="Email для оповещений" name="alertEmail" rules={[{ type: 'email' }]}>
              <Input placeholder="admin@company.com" style={{ maxWidth: 300 }} />
            </Form.Item>
          )}

          <Form.Item label="SNMP Trap-получатели" name="trapDestinations"
            tooltip="IP-адреса через запятую для отправки SNMP-трапов">
            <Input placeholder="192.168.1.100, 192.168.1.101" style={{ maxWidth: 360 }} />
          </Form.Item>

          <Divider orientationMargin={0}>Пороги оповещений</Divider>
          <Space wrap>
            <Form.Item label="Предупреждение о тонере (%)" name="tonerLowThreshold" initialValue={10} style={{ width: 220 }}>
              <InputNumber min={5} max={50} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="Предупреждение о бумаге (листов)" name="paperLowThreshold" initialValue={50} style={{ width: 230 }}>
              <InputNumber min={10} max={500} style={{ width: '100%' }} />
            </Form.Item>
          </Space>

          <Alert
            type="info" showIcon style={{ marginTop: 8 }}
            message="SNMP Trap-оповещения настраиваются через вендорный MIB. Для Kyocera используется KMIB, для Canon — CANONMIB."
          />
        </>
      ),
    },
  ];

  return (
    <Modal
      title={`МФУ / Принтер — ${hostname || 'устройство'}`}
      open={open} onCancel={onClose} footer={null} width={780} destroyOnClose
    >
      <Alert
        type="warning" showIcon style={{ marginBottom: 12 }}
        message="Конфигурация МФУ применяется через SNMP SET-команды. Требует net-snmp на сервере управления и активный SNMP Write Community."
      />

      <Form form={form} layout="vertical" autoComplete="off">
        <Tabs type="card" items={tabItems} />

        <div style={{ marginTop: 16 }}>
          <SaveToMemoryToggle
            value={saveToMemory}
            onChange={setSaveToMemory}
            onLabel="Сохранить в NVRAM принтера"
            offLabel="Только в RAM (до выключения)"
            onDescription="Отправляется SNMP SET с OID сохранения (вендор-специфично: Kyocera, HP и т.д.). Настройки выживают перезагрузку."
            offDescription="Настройки применяются в оперативную память принтера и будут сброшены при его выключении."
          />
        </div>

        <Button type="primary" icon={<ThunderboltOutlined />} loading={submitting}
          onClick={handleSubmit} block style={{ marginTop: 16 }}>
          Применить SNMP-конфигурацию
        </Button>
      </Form>
    </Modal>
  );
}
