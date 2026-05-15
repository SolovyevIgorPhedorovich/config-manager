import { useState } from 'react';
import { Form, Input, Button, Select, message } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';

export default function LoginPage() {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const [form] = Form.useForm(); 

  const from = (location.state as any)?.from?.pathname || '/';

  const handleLogin = async (values: any) => {
    try {
      setLoading(true);
      const res = await fetch('http://localhost:8080/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Auth-Type': values.authType },
        body: JSON.stringify({
          username: values.username,
          password: values.password,
          authType: values.authType
        }),
        credentials: 'include'
      });

      if (res.ok) {
        message.success('Вход выполнен!');
        navigate(from, { replace: true });
      } else {
        throw new Error(res.statusText || 'Ошибка авторизации');
      }
    } catch (err: any) {
      message.error(err.message || 'Ошибка входа');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: 400, margin: '20vh auto', textAlign: 'center' }}>
      <Form form={form} layout="vertical" onFinish={handleLogin}>
        <Form.Item
          name="username"
          label="Логин"
          initialValue="admin"
          rules={[{ required: true }]}
        >
          <Input placeholder="admin" />
        </Form.Item>

        <Form.Item
          name="password"
          label="Пароль"
          initialValue=""
          rules={[{ required: true }]}
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