import React, { useEffect, useState } from 'react';
import { Modal, Form, InputNumber, Input, Select, Button, message, Alert, Divider, Checkbox, Collapse } from 'antd';
import { devicesApi } from '../api/devicesApi';
import type { ScanCredential } from '../types';

export interface ScanOptions {
  ipaddr: string;
  mask: number;
  port: number;
  community: string;
  snmpv: string;
  securityName?: string;
  authProtocol?: string;
  authPassword?: string;
  privProtocol?: string;
  privPassword?: string;
  credentialId?: number;
  sshUsername?: string;
  sshPassword?: string;
  winrmUsername?: string;
  winrmPassword?: string;
}

const SCAN_MODE_LABELS: Record<string, string> = {
  all: 'Все устройства',
  windows: 'ПК (Windows)',
  pc: 'ПК (Windows)',
  linux: 'Linux / VM',
  vm: 'Linux / VM',
  cisco: 'Сетевые устройства Cisco',
  mfu: 'МФУ',
};

export const ScanDeviceModal: React.FC<{
  open: boolean;
  onCancel: () => void;
  onScan: (options: ScanOptions) => Promise<void>;
  scanMode?: string;
}> = ({ open, onCancel, onScan, scanMode }) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [profiles, setProfiles] = useState<ScanCredential[]>([]);
  const [profileId, setProfileId] = useState<number | undefined>(undefined);
  const [saveAsProfile, setSaveAsProfile] = useState(false);
  const snmpVersion = Form.useWatch('snmpv', form);
  const isV3 = snmpVersion === 'v3';

  // Загружаем профили доступа при открытии
  useEffect(() => {
    if (!open) return;
    devicesApi.scanCredentials.getAll()
      .then(setProfiles)
      .catch(() => setProfiles([]));
  }, [open]);

  const useAdHoc = profileId === undefined; // ручной ввод кредов, если профиль не выбран

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);

      const options: ScanOptions = {
        ipaddr: values.ipaddr,
        mask: values.mask,
        port: values.port,
        community: values.community,
        snmpv: values.snmpv,
      };

      // SNMPv3 — передаём логин и пароли аутентификации/шифрования
      if (values.snmpv === 'v3') {
        options.securityName = values.securityName || undefined;
        options.authProtocol = values.authProtocol || undefined;
        options.authPassword = values.authPassword || undefined;
        options.privProtocol = values.privProtocol || undefined;
        options.privPassword = values.privPassword || undefined;
      }

      if (profileId !== undefined) {
        options.credentialId = profileId;
      } else {
        // Опционально сохраняем введённые креды как профиль
        if (saveAsProfile && values.profileName) {
          try {
            const created = await devicesApi.scanCredentials.create({
              name: values.profileName,
              domain: values.domain || undefined,
              sshUsername: values.sshUsername,
              sshPassword: values.sshPassword,
              winrmUsername: values.winrmUsername,
              winrmPassword: values.winrmPassword,
            });
            options.credentialId = created.id;
          } catch (e: any) {
            message.error(`Не удалось сохранить профиль: ${e.response?.data?.message || e.message}`);
            setLoading(false);
            return;
          }
        } else {
          // Если задан домен — приводим WinRM-логин к виду DOMAIN\user
          let winrmUser: string | undefined = values.winrmUsername || undefined;
          if (values.domain && winrmUser && !winrmUser.includes('\\') && !winrmUser.includes('@')) {
            winrmUser = `${values.domain}\\${winrmUser}`;
          }
          options.sshUsername   = values.sshUsername || undefined;
          options.sshPassword   = values.sshPassword || undefined;
          options.winrmUsername = winrmUser;
          options.winrmPassword = values.winrmPassword || undefined;
        }
      }

      await onScan(options);
      onCancel();
    } catch (error) {
      console.error(error);
      message.error('Ошибка запуска сканирования');
    } finally {
      setLoading(false);
    }
  };

  const modeLabel = scanMode ? (SCAN_MODE_LABELS[scanMode] ?? scanMode) : SCAN_MODE_LABELS['all'];

  return (
    <Modal
      title="Автоматический поиск устройств"
      open={open}
      onCancel={onCancel}
      footer={[
        <Button key="cancel" onClick={onCancel}>Отмена</Button>,
        <Button key="submit" type="primary" loading={loading} onClick={handleSubmit}>
          Запустить сканирование
        </Button>,
      ]}
    >
      <Alert
        style={{ marginBottom: 16 }}
        type="info"
        showIcon
        message={`Режим сканирования: ${modeLabel}`}
        description={
          scanMode && scanMode !== 'all'
            ? 'В базу данных будут добавлены только устройства выбранного типа.'
            : 'В базу данных будут добавлены все обнаруженные устройства.'
        }
      />
      <Form form={form} layout="vertical">
        <Form.Item
          name="ipaddr"
          label="Сетевой адрес (начало)"
          initialValue="192.168.1.1"
          rules={[{ required: true, message: 'Введите IP-адрес' }]}
        >
          <Input placeholder="Например: 192.168.1.1" />
        </Form.Item>

        <Form.Item
          name="mask"
          label="Маска сети (CIDR)"
          initialValue={24}
          tooltip="Рекомендуется /24 для локальной сети. Максимум /24 (256 хостов)."
          rules={[{ required: true, message: 'Введите маску' }]}
        >
          <InputNumber min={0} max={24} style={{ width: '100%' }} />
        </Form.Item>

        <Form.Item
          name="port"
          label="SNMP-порт"
          initialValue={161}
          tooltip="Стандартный порт SNMP — 161"
        >
          <InputNumber disabled style={{ width: '100%' }} />
        </Form.Item>

        <Form.Item
          name="community"
          label="Community string"
          initialValue="public"
          tooltip="Для безопасности используйте кастомную community (не public!)"
        >
          <Input />
        </Form.Item>

        <Form.Item
          name="snmpv"
          label="Версия SNMP"
          initialValue="v2c"
          rules={[{ required: true, message: 'Выберите версию SNMP' }]}
        >
          <Select>
            <Select.Option value="v1">SNMP v1</Select.Option>
            <Select.Option value="v2c">SNMP v2c (рекомендуется)</Select.Option>
            <Select.Option value="v3">SNMP v3</Select.Option>
          </Select>
        </Form.Item>

        {isV3 && (
          <>
            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 12 }}
              message="SNMP v3 использует логин и пароли вместо community"
              description="Уровень безопасности определяется автоматически: без паролей — noAuthNoPriv, только пароль аутентификации — authNoPriv, оба пароля — authPriv."
            />
            <Form.Item
              name="securityName"
              label="Логин (Security Name)"
              rules={[{ required: true, message: 'Укажите логин SNMPv3' }]}
            >
              <Input autoComplete="off" placeholder="например, snmpadmin" />
            </Form.Item>
            <Form.Item name="authProtocol" label="Протокол аутентификации" initialValue="SHA">
              <Select options={[
                { value: 'SHA',    label: 'SHA-1' },
                { value: 'SHA256', label: 'SHA-256' },
                { value: 'MD5',    label: 'MD5' },
              ]} />
            </Form.Item>
            <Form.Item name="authPassword" label="Пароль аутентификации" tooltip="Пусто — режим noAuthNoPriv">
              <Input.Password autoComplete="new-password" placeholder="мин. 8 символов" />
            </Form.Item>
            <Form.Item name="privProtocol" label="Протокол шифрования" initialValue="AES">
              <Select options={[
                { value: 'AES',    label: 'AES-128' },
                { value: 'AES256', label: 'AES-256' },
                { value: 'DES',    label: 'DES' },
              ]} />
            </Form.Item>
            <Form.Item name="privPassword" label="Пароль шифрования" tooltip="Требует заполненного пароля аутентификации. Пусто — режим authNoPriv">
              <Input.Password autoComplete="new-password" placeholder="мин. 8 символов" />
            </Form.Item>
          </>
        )}

        <Divider>Учётные данные (SSH / WinRM)</Divider>

        <Form.Item
          label="Профиль доступа"
          tooltip="Сохранённый набор логинов/паролей. Пароли хранятся в зашифрованном виде."
        >
          <Select
            placeholder="Без профиля (ввести вручную)"
            allowClear
            value={profileId}
            onChange={(v) => setProfileId(v)}
            options={profiles.map((p) => ({
              value: p.id,
              label: `${p.name}${p.sshUsername ? ` · ssh:${p.sshUsername}` : ''}${p.winrmUsername ? ` · winrm:${p.winrmUsername}` : ''}`,
            }))}
            notFoundContent="Профилей пока нет"
          />
        </Form.Item>

        {useAdHoc && (
          <Collapse
            ghost
            items={[{
              key: 'creds',
              label: 'Ввести логин/пароль вручную',
              children: (
                <>
                  <Form.Item
                    name="domain"
                    label="Домен (для WinRM, необязательно)"
                    tooltip="Если задан, WinRM-подключение будет от DOMAIN\\логин"
                  >
                    <Input autoComplete="off" placeholder="например, CORP" />
                  </Form.Item>
                  <Form.Item name="sshUsername" label="SSH логин">
                    <Input autoComplete="off" placeholder="например, root" />
                  </Form.Item>
                  <Form.Item name="sshPassword" label="SSH пароль">
                    <Input.Password autoComplete="new-password" />
                  </Form.Item>
                  <Form.Item name="winrmUsername" label="WinRM логин">
                    <Input autoComplete="off" placeholder="например, Administrator" />
                  </Form.Item>
                  <Form.Item name="winrmPassword" label="WinRM пароль">
                    <Input.Password autoComplete="new-password" />
                  </Form.Item>

                  <Checkbox checked={saveAsProfile} onChange={(e) => setSaveAsProfile(e.target.checked)}>
                    Сохранить как профиль
                  </Checkbox>
                  {saveAsProfile && (
                    <Form.Item
                      name="profileName"
                      label="Название профиля"
                      style={{ marginTop: 12 }}
                      rules={[{ required: true, message: 'Введите название профиля' }]}
                    >
                      <Input placeholder="например, Домен office" />
                    </Form.Item>
                  )}
                </>
              ),
            }]}
          />
        )}
      </Form>
    </Modal>
  );
};
