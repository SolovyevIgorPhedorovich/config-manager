import React, { useEffect, useState } from 'react';
import {
  Card, Table, Button, Modal, Form, Input, Select, Switch, Tag, Space, message, Popconfirm,
} from 'antd';
import { PlusOutlined, DeleteOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { userApi } from '../api/userApi';
import type { AppUser } from '../types';

export default function UserManager() {
  const [users, setUsers] = useState<AppUser[]>([]);
  const [roles, setRoles] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  const load = async () => {
    setLoading(true);
    try {
      const [u, r] = await Promise.all([userApi.getAll(), userApi.getRoles().catch(() => [])]);
      setUsers(u);
      setRoles(r);
    } catch (e: any) {
      message.error(`Не удалось загрузить пользователей: ${e.response?.data?.message || e.message}`);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      await userApi.create({
        username: values.username,
        password: values.password,
        email: values.email,
        enabled: values.enabled ?? true,
        roles: values.roles,
      });
      message.success('Пользователь создан');
      setModalOpen(false);
      form.resetFields();
      load();
    } catch (e: any) {
      if (e?.errorFields) return; // ошибки валидации формы
      message.error(`Ошибка создания: ${e.response?.data?.message || e.message}`);
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await userApi.delete(id);
      message.success('Пользователь удалён');
      setUsers((prev) => prev.filter((u) => u.id !== id));
    } catch (e: any) {
      message.error(`Ошибка удаления: ${e.response?.data?.message || e.message}`);
    }
  };

  const columns: ColumnsType<AppUser> = [
    { title: 'Логин', dataIndex: 'username', key: 'username' },
    { title: 'Email', dataIndex: 'email', key: 'email', render: (v) => v || '—' },
    {
      title: 'Роли',
      dataIndex: 'roles',
      key: 'roles',
      render: (rs: string[]) => (rs && rs.length ? rs.map((r) => <Tag key={r} color="blue">{r}</Tag>) : '—'),
    },
    {
      title: 'Активен',
      dataIndex: 'enabled',
      key: 'enabled',
      render: (v: boolean) => <Tag color={v ? 'success' : 'default'}>{v ? 'Да' : 'Нет'}</Tag>,
    },
    {
      title: 'Действия',
      key: 'actions',
      render: (_, record) => (
        <Popconfirm
          title={`Удалить пользователя "${record.username}"?`}
          okText="Удалить"
          okType="danger"
          cancelText="Отмена"
          onConfirm={() => handleDelete(record.id)}
        >
          <Button danger size="small" icon={<DeleteOutlined />} />
        </Popconfirm>
      ),
    },
  ];

  // Предлагаем стандартные роли + уже существующие
  const roleOptions = Array.from(new Set([...roles, 'ADMIN', 'USER'])).map((r) => ({ value: r, label: r }));

  return (
    <Card
      title="Пользователи"
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>Обновить</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => { form.resetFields(); setModalOpen(true); }}>
            Добавить пользователя
          </Button>
        </Space>
      }
    >
      <Table rowKey="id" loading={loading} columns={columns} dataSource={users} pagination={{ pageSize: 10 }} />

      <Modal
        title="Новый пользователь"
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleCreate}
        confirmLoading={saving}
        okText="Создать"
        cancelText="Отмена"
      >
        <Form form={form} layout="vertical" initialValues={{ enabled: true, roles: ['USER'] }}>
          <Form.Item name="username" label="Логин" rules={[{ required: true, message: 'Введите логин' }]}>
            <Input autoComplete="off" placeholder="например, operator" />
          </Form.Item>
          <Form.Item name="password" label="Пароль" rules={[{ required: true, message: 'Введите пароль' }]}>
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item name="email" label="Email" rules={[{ type: 'email', message: 'Некорректный email' }]}>
            <Input placeholder="user@company.local" />
          </Form.Item>
          <Form.Item name="roles" label="Роли">
            <Select mode="tags" placeholder="Выберите или введите роли" options={roleOptions} />
          </Form.Item>
          <Form.Item name="enabled" label="Активен" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
