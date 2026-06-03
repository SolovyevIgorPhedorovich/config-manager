import React, { useEffect, useState } from 'react';
import {
  Modal, Form, Input, Button, Space, Table, Checkbox, Divider,
  Typography, Alert, message, Tag,
} from 'antd';
import { PlusOutlined, DeleteOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { templatesApi, Template, TemplateAssignment, TemplateCredentials } from '../api/templatesApi';

const { Text } = Typography;

interface Variable { key: string; value: string }

interface CredRow extends TemplateAssignment {
  username: string;
  password: string;
  port: string;
}

interface Props {
  open: boolean;
  template: Template | null;
  assignments: TemplateAssignment[];
  onSuccess: (batchId: string) => void;
  onClose: () => void;
}

export default function TemplateApplyModal({
  open, template, assignments, onSuccess, onClose,
}: Props) {
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [variables, setVariables] = useState<Variable[]>([{ key: '', value: '' }]);
  const [credRows, setCredRows] = useState<CredRow[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (open && assignments.length > 0) {
      const ids = assignments.map(a => a.deviceId);
      setSelectedIds(ids);
      setCredRows(assignments.map(a => ({
        ...a, username: '', password: '', port: '',
      })));
      setVariables([{ key: '', value: '' }]);
    }
  }, [open, assignments]);

  // Keep credRows in sync with selectedIds
  useEffect(() => {
    setCredRows(prev =>
      assignments
        .filter(a => selectedIds.includes(a.deviceId))
        .map(a => {
          const existing = prev.find(r => r.deviceId === a.deviceId);
          return existing ?? { ...a, username: '', password: '', port: '' };
        })
    );
  }, [selectedIds, assignments]);

  const updateCred = (deviceId: number, field: keyof CredRow, value: string) => {
    setCredRows(prev =>
      prev.map(r => r.deviceId === deviceId ? { ...r, [field]: value } : r)
    );
  };

  const addVariable = () => setVariables(v => [...v, { key: '', value: '' }]);
  const removeVariable = (idx: number) => setVariables(v => v.filter((_, i) => i !== idx));
  const updateVariable = (idx: number, field: 'key' | 'value', val: string) => {
    setVariables(v => v.map((item, i) => i === idx ? { ...item, [field]: val } : item));
  };

  const validate = (): string | null => {
    if (selectedIds.length === 0) return 'Выберите хотя бы одно устройство';
    for (const row of credRows) {
      if (!row.username.trim()) return `Не указан логин для ${row.deviceHostname}`;
      if (!row.password.trim()) return `Не указан пароль для ${row.deviceHostname}`;
    }
    for (const v of variables) {
      if (v.key && !v.value) return `Не задано значение для переменной "${v.key}"`;
    }
    return null;
  };

  const handleApply = async () => {
    const err = validate();
    if (err) { message.warning(err); return; }

    const varsMap: Record<string, string> = {};
    variables.filter(v => v.key.trim()).forEach(v => { varsMap[v.key.trim()] = v.value; });

    const credentials: Record<number, TemplateCredentials> = {};
    credRows.forEach(r => {
      credentials[r.deviceId] = {
        username: r.username,
        password: r.password,
        port: r.port ? parseInt(r.port, 10) : undefined,
      };
    });

    setLoading(true);
    try {
      const result = await templatesApi.apply(template!.id, {
        deviceIds: selectedIds,
        variables: Object.keys(varsMap).length > 0 ? varsMap : undefined,
        credentials,
      });
      message.success(
        `Применение запущено. Batch ID: ${result.batchId}. Задач: ${result.taskGroupIds.length}`
      );
      onSuccess(result.batchId);
    } catch (e: any) {
      message.error(e?.response?.data?.message || 'Ошибка при применении шаблона');
    } finally {
      setLoading(false);
    }
  };

  const deviceColumns = [
    {
      title: '',
      width: 40,
      render: (_: any, record: TemplateAssignment) => (
        <Checkbox
          checked={selectedIds.includes(record.deviceId)}
          onChange={e => {
            setSelectedIds(prev =>
              e.target.checked
                ? [...prev, record.deviceId]
                : prev.filter(id => id !== record.deviceId)
            );
          }}
        />
      ),
    },
    {
      title: 'Устройство',
      dataIndex: 'deviceHostname',
      render: (h: string, r: TemplateAssignment) => (
        <span>{h} {r.deviceIp && <Tag color="default">{r.deviceIp}</Tag>}</span>
      ),
    },
  ];

  const credColumns = [
    {
      title: 'Устройство',
      dataIndex: 'deviceHostname',
      width: 160,
      render: (h: string, r: CredRow) => (
        <span style={{ fontWeight: 500 }}>{h}</span>
      ),
    },
    {
      title: 'Логин',
      width: 150,
      render: (_: any, r: CredRow) => (
        <Input
          size="small"
          value={r.username}
          onChange={e => updateCred(r.deviceId, 'username', e.target.value)}
          placeholder="admin"
        />
      ),
    },
    {
      title: 'Пароль',
      width: 150,
      render: (_: any, r: CredRow) => (
        <Input.Password
          size="small"
          value={r.password}
          onChange={e => updateCred(r.deviceId, 'password', e.target.value)}
          placeholder="••••••"
        />
      ),
    },
    {
      title: 'Порт',
      width: 80,
      render: (_: any, r: CredRow) => (
        <Input
          size="small"
          value={r.port}
          onChange={e => updateCred(r.deviceId, 'port', e.target.value)}
          placeholder="22"
          style={{ width: 70 }}
        />
      ),
    },
  ];

  return (
    <Modal
      title={
        <Space>
          <ThunderboltOutlined style={{ color: '#faad14' }} />
          <span>Применить шаблон: {template?.name}</span>
        </Space>
      }
      open={open}
      onCancel={onClose}
      width={760}
      footer={
        <Space>
          <Button onClick={onClose}>Отмена</Button>
          <Button
            type="primary"
            icon={<ThunderboltOutlined />}
            loading={loading}
            disabled={selectedIds.length === 0}
            onClick={handleApply}
          >
            Применить к {selectedIds.length} устр.
          </Button>
        </Space>
      }
    >
      {assignments.length === 0 ? (
        <Alert
          type="warning"
          message="Нет привязанных устройств"
          description="Привяжите устройства к шаблону перед применением, либо укажите их в поле deviceIds."
          showIcon
        />
      ) : (
        <>
          {/* ── Выбор устройств ── */}
          <Text strong>Устройства</Text>
          <Table
            size="small"
            dataSource={assignments}
            columns={deviceColumns}
            rowKey="deviceId"
            pagination={false}
            style={{ marginTop: 8, marginBottom: 16 }}
          />

          {/* ── Переменные ── */}
          <Divider orientationMargin={0} style={{ fontSize: 14, margin: '8px 0 12px' }}>
            Переменные шаблона
          </Divider>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Встроенные переменные ({'{{'}{'}}'} device.hostname, device.ip, device.type) подставляются автоматически.
          </Text>

          <div style={{ marginTop: 10, marginBottom: 16 }}>
            {variables.map((v, idx) => (
              <Space key={idx} style={{ display: 'flex', marginBottom: 6 }}>
                <Input
                  size="small"
                  placeholder="Имя переменной"
                  value={v.key}
                  onChange={e => updateVariable(idx, 'key', e.target.value)}
                  style={{ width: 180 }}
                  prefix={<Text type="secondary" style={{ fontSize: 11 }}>{'{{'}  {'}}'}</Text>}
                />
                <Input
                  size="small"
                  placeholder="Значение"
                  value={v.value}
                  onChange={e => updateVariable(idx, 'value', e.target.value)}
                  style={{ width: 220 }}
                />
                <Button
                  size="small"
                  type="text"
                  danger
                  icon={<DeleteOutlined />}
                  onClick={() => removeVariable(idx)}
                  disabled={variables.length === 1}
                />
              </Space>
            ))}
            <Button
              size="small"
              type="dashed"
              icon={<PlusOutlined />}
              onClick={addVariable}
              style={{ marginTop: 4 }}
            >
              Добавить переменную
            </Button>
          </div>

          {/* ── Учётные данные ── */}
          {credRows.length > 0 && (
            <>
              <Divider orientationMargin={0} style={{ fontSize: 14, margin: '8px 0 12px' }}>
                Учётные данные
              </Divider>
              <Table
                size="small"
                dataSource={credRows}
                columns={credColumns}
                rowKey="deviceId"
                pagination={false}
              />
            </>
          )}
        </>
      )}
    </Modal>
  );
}
