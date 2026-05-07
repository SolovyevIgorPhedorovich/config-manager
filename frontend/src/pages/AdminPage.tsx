import React, { useState } from 'react';
import { Card, Form, Input, Switch, Button, message, Tabs, InputNumber, Modal } from 'antd';
import { Settings } from '../types';

const { TabPane } = Tabs;

export default function AdminPage() {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [changePasswordModalOpen, setChangePasswordModalOpen] = useState(false);
  const adEnabled = Form.useWatch('adEnabled', form)

  React.useEffect(() => {
    form.setFieldsValue({
      apiUrl: 'http://localhost:8080/api',
      autoRefreshInterval: 30,
      enableNotifications: true,
      adEnabled: false,
      adUrl: 'ldap://dc.company.local:389',
      adBaseDn: 'DC=company,DC=local',
      adUserSearchFilter: '(sAMAccountName={0})'
    });
  }, [form]);

  const handleSubmit = async (values: any) => {
    try {
      setLoading(true);
      // TODO: сохранить настройки (API)
      await fetch('/api/settings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(values)
      });
      console.log('Сохранено:', values);
      message.success('Настройки сохранены');
    } catch (err) {
      message.error('Ошибка при сохранении');
    } finally {
      setLoading(false);
    }
  };

  const handleChangePassword = async (values: any) => {
    try {
      await fetch('/api/auth/change-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(values)
      });
      message.success('Пароль изменён');
      setChangePasswordModalOpen(false);
    } catch (err) {
      message.error('Ошибка изменения пароля');
    }
  };

   return (
    <>
      <Tabs defaultActiveKey="1">
        {/* Общие настройки */}
        <TabPane tab="⚙️ Общие настройки" key="1">
          <Card title="Настройка подключения к backend">
            <Form form={form} layout="vertical" onFinish={handleSubmit}>
              <Form.Item
                name="apiUrl"
                label="URL API"
                rules={[{ required: true }]}
              >
                <Input placeholder="http://localhost:8080/api" />
              </Form.Item>

              <Form.Item
                name="autoRefreshInterval"
                label="Интервал автообновления (сек)"
                rules={[{ required: true, type: 'number', min: 5 }]}
              >
                <InputNumber min={5} style={{ width: '100%' }} />
              </Form.Item>

              <Form.Item name="enableNotifications" label="Уведомления" valuePropName="checked">
                <Switch defaultChecked />
              </Form.Item>

              <Button type="primary" htmlType="submit" loading={loading}>
                Сохранить
              </Button>
            </Form>
          </Card>
        </TabPane>

        {/* Настройки AD */}
        <TabPane tab="👥 Active Directory" key="2">
          <Card title="Настройка интеграции с Active Directory">
            <Form form={form} layout="vertical" onFinish={handleSubmit}>
              <Form.Item
                name="adEnabled"
                label="Включить AD"
                valuePropName="checked"
              >
                <Switch />
              </Form.Item>

              <Form.Item
                name="adUrl"
                label="URL LDAP (AD)"
                rules={[{ required: true }]}
              > <Input
                placeholder="ldap://dc.company.local:389"
                disabled={!adEnabled}
              />
            </Form.Item>

              <Form.Item name="adBaseDn" label="Базовый DN">
            <Input
              placeholder="DC=company,DC=local"
              disabled={!adEnabled}
            />
          </Form.Item>


                <Form.Item name="adUserSearchFilter" label="Фильтр поиска пользователя">
              <Input
                placeholder="(sAMAccountName={0})"
                disabled={!adEnabled}
              />
            </Form.Item>

              <Button type="primary" htmlType="submit" loading={loading}>
                Сохранить настройки AD
              </Button>
            </Form>
          </Card>
        </TabPane>

        {/* Смена пароля */}
        <TabPane tab="🔑 Смена пароля admin" key="3">
          <Card title="Изменение пароля пользователя admin">
            <Button type="primary" onClick={() => setChangePasswordModalOpen(true)}>
              Изменить пароль
            </Button>
          </Card>
        </TabPane>

        {/* Отчёты */}
        <TabPane tab="📊 Отчёты" key="4">
          <Card>Здесь будут отчёты об изменениях и статистике.</Card>
        </TabPane>
      </Tabs>

      {/* Модальное окно смены пароля */}
      <Modal
        title="Изменить пароль администратора"
        open={changePasswordModalOpen}
        onCancel={() => setChangePasswordModalOpen(false)}
        footer={null}
      >
        <Form onFinish={handleChangePassword} layout="vertical">
          <Form.Item
            name="currentPassword"
            label="Текущий пароль"
            rules={[{ required: true }]}
          >
            <Input.Password />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label="Новый пароль"
            rules={[
              { required: true },
              { min: 8, message: 'Минимум 8 символов' }
            ]}
          >
            <Input.Password />
          </Form.Item>
          <Form.Item
            name="confirmNewPassword"
            label="Повторите пароль"
            dependencies={['newPassword']}
            rules={[
              { required: true },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error('Пароли не совпадают'));
                }
              })
            ]}
          >
            <Input.Password />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>Сохранить</Button>
        </Form>
      </Modal>
    </>
  );
}