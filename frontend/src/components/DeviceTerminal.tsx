import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Modal, Button, Form, InputNumber, Input, message, Typography } from 'antd';
import { Terminal as XTerm } from 'xterm';
import { FitAddon } from 'xterm-addon-fit';
import 'xterm/css/xterm.css'; // обязателен: позиционирует helper-textarea, через который xterm ловит клавиатуру
import { devicesApi } from '../api/devicesApi';

const { Text } = Typography;

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
  const defaultUser = device?.os?.toUpperCase() === 'WINDOWS' ? 'Administrator' : 'root';

  // Надёжный фокус терминала: xterm.focus() + прямой фокус на helper-textarea
  // (в Firefox это критично — через него xterm перехватывает клавиатуру).
  const focusTerminal = useCallback(() => {
    const term = terminalRef.current;
    if (!term) return;
    term.focus();
    // Фокус на textarea ИМЕННО живого терминала (term.element), а не на первом
    // .xterm-helper-textarea в DOM — иначе при оставшемся «зомби» фокус уходит
    // на чужой элемент без обработчиков, и клавиши никуда не идут.
    const el = (term as unknown as { element?: HTMLElement }).element;
    const ta = el?.querySelector('.xterm-helper-textarea') as HTMLTextAreaElement | null;
    ta?.focus();
  }, []);

  useEffect(() => {
    if (device && open) {
      form.setFieldsValue({
        host: device.ip || 'localhost',
        user: device.os?.toUpperCase() === 'WINDOWS' ? 'Administrator' : 'root',
        port: device.os?.toUpperCase() === 'WINDOWS' ? 5985 : 22,
      });
    }
  }, [device, open, form]);

  useEffect(() => {
    if (!open) return;

    const initTerminal = () => {
      if (!termRef.current) return;

      // Защита от «зомби»: убираем прежний инстанс и его DOM, чтобы в контейнере
      // был ровно один терминал (иначе фокус уходит на чужой textarea).
      terminalRef.current?.dispose();
      termRef.current.innerHTML = '';

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

      // Стартовая строка: позиционирует курсор → helper-textarea получает
      // реальные размеры/позицию (в Firefox 0×0 off-screen textarea может не
      // получать keydown).
      term.writeln('\x1b[90mТерминал готов. Заполните поля и нажмите Connect.\x1b[0m');

      // ДИАГНОСТИКА: доходят ли нажатия до xterm (keydown) — лог в консоль.
      term.attachCustomKeyEventHandler((e) => {
        if (e.type === 'keydown') {
          // eslint-disable-next-line no-console
          console.log('[terminal] keydown:', e.key, 'code=', e.code);
        }
        return true; // не блокируем обработку
      });

      term.onData((data) => {
        // eslint-disable-next-line no-console
        console.log(
          '[terminal] onData:', JSON.stringify(data),
          '| wsOpen=', socketRef.current?.readyState === WebSocket.OPEN,
          '| sessionId=', sessionIdRef.current
        );
        if (socketRef.current?.readyState === WebSocket.OPEN && sessionIdRef.current) {
          const isWindows = device?.os?.toUpperCase() === 'WINDOWS';

          if (!isWindows) {
            // Linux/SSH: интерактивный PTY — шлём сырые нажатия, эхо приходит от сервера
            socketRef.current.send(
              JSON.stringify({
                sessionId: sessionIdRef.current,
                type: 'input',
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
                    type: 'input',
                    input: command + '\r\n',
                  })
                );
                inputBufferRef.current = '';
              } else {
                socketRef.current.send(
                  JSON.stringify({
                    sessionId: sessionIdRef.current,
                    type: 'input',
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
                  type: 'input',
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

      // При изменении размера терминала синхронизируем PTY на сервере
      term.onResize(({ cols, rows }) => {
        if (socketRef.current?.readyState === WebSocket.OPEN && sessionIdRef.current) {
          socketRef.current.send(
            JSON.stringify({ sessionId: sessionIdRef.current, type: 'resize', cols, rows })
          );
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

      const res = await devicesApi.terminalSession(device.id, {
        host: values.host,
        port: values.port,
        user: values.user,
        password: values.password,
      });

      const session = res.data;
      sessionIdRef.current = session.sessionId;

      const token = localStorage.getItem('token');

      const socket = new WebSocket(
        `ws://localhost:8080/ws/terminal?token=${token}`
      );
      // КРИТИЧНО: сохраняем сокет в ref — иначе onData видит socketRef.current=null,
      // гейт wsOpen всегда ложный, и нажатия не уходят на сервер.
      socketRef.current = socket;

      socket.onopen = () => {
        message.success('Соединение установлено');
        // Привязываем WebSocket к терминальной сессии и запускаем стриминг вывода.
        const term = terminalRef.current;
        fitAddonRef.current?.fit();
        socket.send(
          JSON.stringify({
            sessionId: sessionIdRef.current,
            type: 'init',
            cols: term?.cols ?? 80,
            rows: term?.rows ?? 24,
          })
        );
        // AntD Modal (rc-dialog) после анимации открытия сам фокусирует панель
        // модалки и перебивает наш ранний focus(). Ставим фокус несколько раз,
        // чтобы выиграть гонку с этим пост-анимационным фокусом.
        [50, 350, 600].forEach((d) => setTimeout(focusTerminal, d));
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
        // eslint-disable-next-line no-console
        console.log('[terminal] WS error', err);
        message.error('WebSocket ошибка');
      };

      socket.onclose = (event) => {
        // eslint-disable-next-line no-console
        console.log('[terminal] WS closed: code=', event.code, 'reason=', event.reason, 'wasClean=', event.wasClean);
        if (event.wasClean) {
          message.info('Соединение закрыто');
        } else {
          message.warning('Соединение разорвано');
        }
        terminalRef.current?.write(
          `\r\n\x1b[31mСоединение закрыто (code ${event.code}${event.reason ? ', ' + event.reason : ''})\x1b[0m\r\n`
        );
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
      // Фокус после завершения анимации открытия — когда rc-dialog уже
      // выполнил свой автофокус на панель модалки и не перебьёт наш.
      afterOpenChange={(opened) => {
        if (opened) setTimeout(focusTerminal, 50);
      }}
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
        // Фокусируем на mouseUp/click БЕЗ preventDefault: в Firefox preventDefault
        // на mousedown подавляет фокус (это штатный приём «не забирать фокус»),
        // из-за чего клавиатура переставала перехватываться.
        onMouseUp={focusTerminal}
        onClick={focusTerminal}
      >
        <div ref={termRef} style={{ height: '60vh' }} />
      </div>

      <Text type="secondary">
        {device?.os?.toUpperCase() === 'WINDOWS'
          ? 'Команды для WINDOWS отправляются после нажатия Enter.'
          : 'Протокол выбирается автоматически (SSH / WinRM).'}
      </Text>
    </Modal>
  );
}