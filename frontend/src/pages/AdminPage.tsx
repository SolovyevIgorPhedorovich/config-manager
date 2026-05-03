import React, { useState } from 'react';
import { Card, Form, Input, Switch, Button, message, Tabs, InputNumber } from 'antd';

const { TabPane } = Tabs;

export default function AdminPage() {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (values: any) => {
    try {
      setLoading(true);
      // TODO: сохранить настройки (API)
      console.log('Сохранено:', values);
      message.success('Настройки сохранены');
    } catch (err) {
      message.error('Ошибка при сохранении');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Tabs defaultActiveKey="1">
      <TabPane tab="⚙️ Общие настройки" key="1">
        <Card title="Настройка подключения к backend">
          <Form form={form} layout="vertical" onFinish={handleSubmit}>
            <Form.Item
              name="apiUrl"
              label="URL API"
              initialValue="http://localhost:8080/api"
              rules={[{ required: true }]}
            >
              <Input placeholder="http://localhost:8080/api" />
            </Form.Item>

            <Form.Item
              name="autoRefreshInterval"
              label="Интервал автообновления (сек)"
              initialValue={30}
              rules={[{ required: true, type: 'number', min: 5 }]}
            >
              <InputNumber min={5} />
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

      <TabPane tab="📊 Отчёты" key="2">
        <Card>Здесь будут отчёты об изменениях и статистике.</Card>
      </TabPane>
    </Tabs>
  );
}