import React, { useEffect, useRef, useState } from 'react';
import { Modal, Button, Form, InputNumber, Input, message, Typography } from 'antd';
import { Terminal as XTerm } from 'xterm';
import { FitAddon } from 'xterm-addon-fit';

const { Text } = Typography;

// Принудительно скрываем вспомогательный textarea (чтобы не мешал)
const hideHelperTextareaStyle = `
  .xterm-helper-textarea {
    position: absolute !important;
    left: -99999em !important;
    top: -99999em !important;
    opacity: 0 !important;
    width: 1px !important;
    height: 1px !important;
    z-index: -1 !important;
  }
`;

if (typeof document !== 'undefined' && !document.querySelector('#xterm-helper-hide-style')) {
  const style = document.createElement('style');
  style.id = 'xterm-helper-hide-style';
  style.textContent = hideHelperTextareaStyle;
  document.head.appendChild(style);
}

interface Device {
  id: number;
  hostname: string;
  ip: string;
  os: string;
}

interface Props {
  open: boolean;
  onClose: () => void;
  device: Device | null;
}

export default function DeviceTerminal({ open, onClose, device }: Props) {
  const termRef = useRef<HTMLDivElement>(null);
  const terminalRef = useRef<XTerm | null>(null);
  const fitAddonRef = useRef<FitAddon | null>(null);
  const socketRef = useRef<WebSocket | null>(null);
  const sessionIdRef = useRef<string | null>(null);
  const inputBufferRef = useRef<string>('');

  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();

  const defaultHost = device?.ip || 'localhost';
  const defaultUser = device?.os === 'WINDOWS' ? 'Administrator' : 'root';

  useEffect(() => {
    if (device && open) {
      form.setFieldsValue({
        host: device.ip || 'localhost',
        user: device.os === 'WINDOWS' ? 'Administrator' : 'root',
        port: device.os === 'WINDOWS' ? 5985 : 22,
      });
    }
  }, [device, open, form]);

  useEffect(() => {
    if (!open) return;

    const initTerminal = () => {
      if (!termRef.current) return;

      const term = new XTerm({
        fontSize: 14,
        cursorBlink: true,
        scrollback: 5000,
        theme: {
          background: '#0d0d0d',
          foreground: '#f0f0f0',
        },
      });

      const fitAddon = new FitAddon();
      term.loadAddon(fitAddon);
      fitAddonRef.current = fitAddon;

      term.open(termRef.current);
      fitAddon.fit();
      term.focus();

      term.onData((data) => {
        if (socketRef.current?.readyState === WebSocket.OPEN && sessionIdRef.current) {
          const isWindows = device?.os === 'WINDOWS';

          if (!isWindows) {
            // Linux/SSH: каждый символ сразу
            socketRef.current.send(
              JSON.stringify({
                sessionId: sessionIdRef.current,
                input: data,
              })
            );
          } else {
            // Windows: буферизация до Enter
            if (data === '\r') {
              const command = inputBufferRef.current;
              if (command) {
                socketRef.current.send(
                  JSON.stringify({
                    sessionId: sessionIdRef.current,
                    input: command + '\r\n',
                  })
                );
                inputBufferRef.current = '';
              } else {
                socketRef.current.send(
                  JSON.stringify({
                    sessionId: sessionIdRef.current,
                    input: '\r\n',
                  })
                );
              }
              term.write('\r\n');
            } else if (data === '\x7f') {
              if (inputBufferRef.current.length > 0) {
                inputBufferRef.current = inputBufferRef.current.slice(0, -1);
                term.write('\b \b');
              }
            } else if (data.charCodeAt(0) < 32) {
              // Управляющие символы (стрелки, Ctrl+C) отправляем сразу
              socketRef.current.send(
                JSON.stringify({
                  sessionId: sessionIdRef.current,
                  input: data,
                })
              );
            } else {
              inputBufferRef.current += data;
              term.write(data);
            }
          }
        }
      });

      terminalRef.current = term;
    };

    const timer = setTimeout(initTerminal, 100);

    const handleResize = () => {
      fitAddonRef.current?.fit();
    };
    window.addEventListener('resize', handleResize);

    return () => {
      clearTimeout(timer);
      window.removeEventListener('resize', handleResize);
      terminalRef.current?.dispose();
      terminalRef.current = null;
      fitAddonRef.current = null;
    };
  }, [open, device?.os]);

  useEffect(() => {
    if (!open) {
      if (socketRef.current?.readyState === WebSocket.OPEN) {
        socketRef.current.close();
      }
      socketRef.current = null;
      sessionIdRef.current = null;
      inputBufferRef.current = '';
    }
  }, [open]);

  const handleConnect = async (values: {
    host: string;
    port: number;
    user: string;
    password?: string;
  }) => {
    if (!device) return;

    if (socketRef.current?.readyState === WebSocket.OPEN) {
      socketRef.current.close();
    }
    sessionIdRef.current = null;
    inputBufferRef.current = '';
    terminalRef.current?.clear();

    try {
      setLoading(true);

      const response = await fetch(
        `http://localhost:8080/api/v1/devices/${device.id}/terminal/session`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(values),
        }
      );

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(errorText || `HTTP ${response.status}`);
      }

      const session = await response.json();
      sessionIdRef.current = session.sessionId;

      const socket = new WebSocket('ws://localhost:8080/ws/terminal');
      socketRef.current = socket;

      socket.onopen = () => {
        message.success('Соединение установлено');
        setTimeout(() => terminalRef.current?.focus(), 200);
      };

      socket.onmessage = (event) => {
        const term = terminalRef.current;
        if (!term) return;

        try {
          const data = JSON.parse(event.data);
          if (data.output !== undefined) {
            term.write(data.output);
          }
          if (data.stdout !== undefined) {
            term.write(data.stdout);
          }
          if (data.stderr !== undefined) {
            term.write(`\x1b[31m${data.stderr}\x1b[0m`);
          }
          if (data.success === false && !data.stderr && !data.stdout && !data.output) {
            term.write(`\r\n\x1b[31mCommand failed with exit code ${data.exitCode ?? 'unknown'}\x1b[0m\r\n`);
          }
        } catch {
          term.write(event.data);
        }
      };

      socket.onerror = (err) => {
        console.error('WebSocket error', err);
        message.error('WebSocket ошибка');
      };

      socket.onclose = (event) => {
        if (event.wasClean) {
          message.info('Соединение закрыто');
        } else {
          message.warning('Соединение разорвано');
        }
        terminalRef.current?.write('\r\n\x1b[31mСоединение закрыто\x1b[0m\r\n');
        inputBufferRef.current = '';
      };
    } catch (err: any) {
      message.error(`Ошибка подключения: ${err.message}`);
      terminalRef.current?.write(`\r\n\x1b[31mОшибка: ${err.message}\x1b[0m\r\n`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title={`Terminal: ${device?.hostname ?? ''} (${device?.os ?? ''})`}
      open={open}
      keyboard={false}
      maskClosable={false}
      onCancel={onClose}
      width="90%"
      footer={null}
      destroyOnClose
    >
      <Form
        form={form}
        layout="inline"
        onFinish={handleConnect}
        initialValues={{
          host: defaultHost,
          user: defaultUser,
          port: 22,
        }}
      >
        <Form.Item name="host" label="Host" rules={[{ required: true }]}>
          <Input />
        </Form.Item>

        <Form.Item name="user" label="User" rules={[{ required: true }]}>
          <Input />
        </Form.Item>

        <Form.Item name="password" label="Password">
          <Input.Password />
        </Form.Item>

        <Form.Item name="port" label="Port" rules={[{ required: true }]}>
          <InputNumber min={1} max={65535} />
        </Form.Item>

        <Button type="primary" htmlType="submit" loading={loading}>
          Connect ({device?.os})
        </Button>
      </Form>

      <div
        style={{ background: '#111', marginTop: 12, padding: 8 }}
        onClick={() => terminalRef.current?.focus()}
      >
        <div ref={termRef} style={{ height: '60vh' }} />
      </div>

      <Text type="secondary">
        {device?.os === 'WINDOWS'
          ? 'Команды для WINDOWS отправляются после нажатия Enter.'
          : 'Протокол выбирается автоматически (SSH / WinRM).'}
      </Text>
    </Modal>
  );
}