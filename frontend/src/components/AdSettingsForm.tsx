import { useEffect, useState } from 'react';
import { Card, Form, Input, Switch, Button, Space, message, Spin, Alert } from 'antd';
import { settingsApi, type AdSettingsRequest } from '../api/settingsApi';
import { getErrorMessage } from '../utils/errorMessage';

/**
 * Редактирование настроек доменной аутентификации (AD/LDAP).
 * Загружает текущие настройки, позволяет проверить подключение и сохранить.
 * Пароль bind-учётки наружу не отдаётся: поле оставляют пустым, чтобы не менять.
 */
export default function AdSettingsForm() {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [enabled, setEnabled] = useState(false);
  const [passwordSet, setPasswordSet] = useState(false);

  const load = async () => {
    try {
      const s = await settingsApi.getAd();
      form.setFieldsValue({
        enabled: s.enabled,
        url: s.url,
        baseDn: s.baseDn,
        userDn: s.userDn ?? '',
        password: '',
        userSearchFilter: s.userSearchFilter,
      });
      setEnabled(s.enabled);
      setPasswordSet(s.passwordSet);
    } catch (e) {
      message.error(getErrorMessage(e, 'Не удалось загрузить настройки AD'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  // Собираем payload: пустой пароль не отправляем (значит — не менять)
  const buildPayload = (): AdSettingsRequest => {
    const v = form.getFieldsValue();
    const payload: AdSettingsRequest = {
      enabled: v.enabled,
      url: v.url,
      baseDn: v.baseDn,
      userDn: v.userDn ?? '',
      userSearchFilter: v.userSearchFilter,
    };
    if (v.password) payload.password = v.password;
    return payload;
  };

  const handleSave = async () => {
    try {
      await form.validateFields();
    } catch { return; }
    setSaving(true);
    try {
      const s = await settingsApi.updateAd(buildPayload());
      setPasswordSet(s.passwordSet);
      form.setFieldValue('password', '');
      message.success('Настройки AD сохранены');
    } catch (e) {
      message.error(getErrorMessage(e, 'Ошибка сохранения настроек AD'));
    } finally {
      setSaving(false);
    }
  };

  const handleTest = async () => {
    setTesting(true);
    try {
      const res = await settingsApi.testAd(buildPayload());
      if (res.success) message.success(res.message || 'Подключение установлено');
      else message.error(`Не удалось подключиться: ${res.error}`);
    } catch (e) {
      message.error(getErrorMessage(e, 'Ошибка проверки подключения'));
    } finally {
      setTesting(false);
    }
  };

  if (loading) return <Spin style={{ display: 'block', margin: '10vh auto' }} />;

  return (
    <Card title="Настройка интеграции с Active Directory / LDAP">
      <Form form={form} layout="vertical" onValuesChange={(_, all) => setEnabled(all.enabled)}>
        <Form.Item name="enabled" label="Доменная аутентификация (AD)" valuePropName="checked">
          <Switch />
        </Form.Item>
        <Form.Item name="url" label="URL LDAP" rules={[{ required: true, message: 'Укажите URL' }]}>
          <Input placeholder="ldap://dc.company.local:389" disabled={!enabled} />
        </Form.Item>
        <Form.Item name="baseDn" label="Базовый DN" rules={[{ required: true, message: 'Укажите базовый DN' }]}>
          <Input placeholder="DC=company,DC=local" disabled={!enabled} />
        </Form.Item>
        <Form.Item name="userDn" label="Сервисная учётка (bind DN / UPN)"
          tooltip="Используется для поиска пользователей в каталоге. Можно оставить пустым, если разрешён анонимный поиск.">
          <Input placeholder="config_manager@test.ru" disabled={!enabled} />
        </Form.Item>
        <Form.Item name="password" label="Пароль сервисной учётки"
          tooltip="Оставьте пустым, чтобы не менять сохранённый пароль.">
          <Input.Password
            placeholder={passwordSet ? '•••••••• (сохранён — оставьте пустым, чтобы не менять)' : 'не задан'}
            disabled={!enabled}
            autoComplete="new-password"
          />
        </Form.Item>
        <Form.Item name="userSearchFilter" label="Фильтр поиска пользователя"
          rules={[{ required: true, message: 'Укажите фильтр' }]}>
          <Input placeholder="(sAMAccountName={0})" disabled={!enabled} />
        </Form.Item>

        {!enabled && (
          <Alert type="info" showIcon style={{ marginBottom: 16 }}
            message="AD выключен — вход выполняется по локальной базе данных." />
        )}

        <Space>
          <Button type="primary" onClick={handleSave} loading={saving}>Сохранить</Button>
          <Button onClick={handleTest} loading={testing} disabled={!enabled}>Проверить подключение</Button>
        </Space>
      </Form>
    </Card>
  );
}
