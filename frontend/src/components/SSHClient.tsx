// src/components/SSHClient.tsx

import React, { useEffect, useRef, useState } from 'react';
import { Modal, Button, Form, InputNumber, Input, message, Typography, Space } from 'antd';
import { Terminal as XTerm } from 'xterm';
import { FitAddon } from 'xterm-addon-fit';

const { Title, Text } = Typography;

interface SSHClientProps {
  open: boolean;
  onClose: () => void;
  device: { hostname: string; ip: string } | null;
}

export default function SSHClient({ open, onClose, device }: SSHClientProps) {
  const termRef = useRef<HTMLDivElement>(null);
  const terminalRef = useRef<XTerm | null>(null);
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();

  // Настройки по умолчанию
  const defaultHost = device?.ip || 'localhost';
  const defaultUser = 'root';

  useEffect(() => {
    if (open && termRef.current) {
      // Инициализация терминала
      const term = new XTerm({
        fontSize: 14,
        lineHeight: 1.2,
        theme: {
          background: '#0d0d0d',
          foreground: '#f0f0f0',
        },
      });

      // Адаптер под размер контейнера
      const fitAddon = new FitAddon();
      term.loadAddon(fitAddon);
      term.open(termRef.current);
      fitAddon.fit();

      // Обработка resize
      window.addEventListener('resize', () => fitAddon.fit());

      terminalRef.current = term;

      // Очистка при закрытии
      return () => {
        window.removeEventListener('resize', () => {});
      };
    }
  }, [open]);

  const handleConnect = async (values: { host: string; port: number; user: string; password?: string }) => {
    try {
      setLoading(true);

      const wsUrl = `ws://localhost:8080/api/ssh/connect?host=${values.host}&port=${values.port || 22}&user=${values.user}`;
      
      const socket = new WebSocket(wsUrl);

      socket.onopen = () => {
        message.success(`Подключено к ${values.host}`);
        if (terminalRef.current) {
          terminalRef.current.focus();
          socket.send('echo "SSH-подключение установлено"\n');
        }
      };

      socket.onmessage = (event) => {
        const data = JSON.parse(event.data);
        if (data.output && terminalRef.current) {
          terminalRef.current.write(data.output);
        }
      };

      socket.onerror = () => {
        message.error('Ошибка подключения. Проверьте настройки backend.');
        setLoading(false);
      };

      // Отправка ввода пользователя
      if (terminalRef.current) {
        terminalRef.current.onData((data) => {
          socket.send(JSON.stringify({ input: data }));
        });
      }

    } catch (err) {
      console.error('SSH ошибка:', err);
      message.error('Не удалось подключиться');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title={`SSH-подключение к ${device?.hostname || 'устройству'}`}
      open={open}
      onCancel={onClose}
      width="90%"
      height="80vh"
      footer={null}
      destroyOnClose
    >
      {/* Форма подключения */}
      <Form
        form={form}
        layout="inline"
        onFinish={handleConnect}
        initialValues={{ host: defaultHost, user: defaultUser, port: 22 }}
        style={{ marginBottom: 16 }}
      >
        <Form.Item name="host" label="Хост" rules={[{ required: true }]}>
          <Input placeholder={defaultHost} disabled />
        </Form.Item>
        <Form.Item name="user" label="Пользователь" rules={[{ required: true }]}>
          <Input placeholder="root или admin" />
        </Form.Item>
        <Form.Item name="port" label="Порт">
          <InputNumber min={1} max={65535} defaultValue={22} />
        </Form.Item>
        <Button type="primary" htmlType="submit" loading={loading}>
          Подключиться
        </Button>
      </Form>

      {/* Терминал */}
      <div style={{ background: '#1e1e1e', padding: 8, borderRadius: 4 }}>
        <div ref={termRef} style={{ height: '60vh' }} />
      </div>

      <Text type="secondary" style={{ marginTop: 8 }}>
        Для работы SSH через браузер необходим backend-сервис (см. ниже).
      </Text>
    </Modal>
  );
}
