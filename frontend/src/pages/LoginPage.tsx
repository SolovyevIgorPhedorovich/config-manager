// pages/LoginPage.tsx
import { useState } from 'react';
import { Form, Input, Button, Select, message } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { authApi } from '../api/authApi';

export default function LoginPage() {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const [form] = Form.useForm();

  const from = (location.state as any)?.from?.pathname || '/';

  const handleLogin = async (values: any) => {
    try {
      setLoading(true);
      
      // Очищаем старые данные перед логином
      localStorage.clear();
      
      const response = await authApi.login({
        username: values.username,
        password: values.password,
        authType: values.authType
      });
      
      // Сохраняем новые данные
      if (response.token) {
        localStorage.setItem('token', response.token);
      }
      if (response.username) {
        localStorage.setItem('username', response.username);
      }
      if (response.role) {
        localStorage.setItem('role', response.role);
      }
      
      message.success('Вход выполнен!');
      navigate(from, { replace: true });
      
    } catch (err: any) {
      console.error('Login error:', err);
      message.error(err.response?.data?.message || 'Ошибка входа');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: 400, margin: '20vh auto', textAlign: 'center' }}>
      <h2>🔐 Вход в систему</h2>
      <Form form={form} layout="vertical" onFinish={handleLogin}>
        <Form.Item
          name="username"
          label="Логин"
          initialValue="admin"
          rules={[{ required: true, message: 'Введите логин' }]}
        >
          <Input placeholder="admin" />
        </Form.Item>

        <Form.Item
          name="password"
          label="Пароль"
          rules={[{ required: true, message: 'Введите пароль' }]}
        >
          <Input.Password placeholder="••••••" />
        </Form.Item>

        <Form.Item
          name="authType"
          label="Способ авторизации"
          initialValue="DB"
          rules={[{ required: true }]}
        >
          <Select
            options={[
              { value: "DB", label: "База данных" },
              { value: "AD", label: "AD / LDAP" }
            ]}
          />
        </Form.Item>

        <Button
          type="primary"
          htmlType="submit"
          loading={loading}
          block
          style={{ marginTop: 16 }}
        >
          Войти
        </Button>
      </Form>
    </div>
  );
}