import React, { useEffect, useState } from 'react';
import {
  Modal, Form, Input, Button, Space, Alert, Typography, Tooltip,
} from 'antd';
import { FormatPainterOutlined, InfoCircleOutlined } from '@ant-design/icons';
import { templatesApi, Template } from '../api/templatesApi';

const { TextArea } = Input;
const { Text } = Typography;

interface Props {
  open: boolean;
  template?: Template;
  onSuccess: (t: Template) => void;
  onClose: () => void;
}

const VARIABLE_HINT = `Встроенные переменные (подставляются автоматически):
  {{device.hostname}} — имя хоста устройства
  {{device.ip}}       — основной IP-адрес
  {{device.type}}     — тип (WINDOWS, LINUX, CISCO …)

Пользовательские переменные задаются при применении шаблона.`;

const DEFAULT_CONTENT = JSON.stringify(
  { hostname: '{{device.hostname}}', dns: ['8.8.8.8'], custom_param: '{{my_var}}' },
  null,
  2,
);

export default function TemplateFormModal({ open, template, onSuccess, onClose }: Props) {
  const [form] = Form.useForm();
  const [jsonStr, setJsonStr] = useState(DEFAULT_CONTENT);
  const [jsonError, setJsonError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const isEdit = !!template;

  useEffect(() => {
    if (open) {
      if (template) {
        form.setFieldsValue({ name: template.name, description: template.description });
        setJsonStr(JSON.stringify(template.content, null, 2));
      } else {
        form.resetFields();
        setJsonStr(DEFAULT_CONTENT);
      }
      setJsonError(null);
    }
  }, [open, template]);

  const validateJson = (value: string): Record<string, any> | null => {
    try {
      const parsed = JSON.parse(value);
      if (typeof parsed !== 'object' || Array.isArray(parsed)) {
        setJsonError('Контент шаблона должен быть JSON-объектом (не массивом)');
        return null;
      }
      setJsonError(null);
      return parsed;
    } catch (e: any) {
      setJsonError(`Невалидный JSON: ${e.message}`);
      return null;
    }
  };

  const handleJsonChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setJsonStr(e.target.value);
    validateJson(e.target.value);
  };

  const handleFormat = () => {
    const parsed = validateJson(jsonStr);
    if (parsed) setJsonStr(JSON.stringify(parsed, null, 2));
  };

  const handleSubmit = async () => {
    const fields = await form.validateFields();
    const parsed = validateJson(jsonStr);
    if (!parsed) return;

    setLoading(true);
    try {
      const payload = { name: fields.name, description: fields.description, content: parsed };
      const result = isEdit
        ? await templatesApi.update(template!.id, payload)
        : await templatesApi.create(payload);
      onSuccess(result);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title={isEdit ? `Редактировать шаблон: ${template!.name}` : 'Создать шаблон'}
      open={open}
      onCancel={onClose}
      width={720}
      footer={
        <Space>
          <Button onClick={onClose}>Отмена</Button>
          <Button type="primary" loading={loading} onClick={handleSubmit}>
            {isEdit ? 'Сохранить' : 'Создать'}
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="name"
          label="Название"
          rules={[{ required: true, message: 'Введите название шаблона' }]}
        >
          <Input placeholder="Например: Windows baseline" />
        </Form.Item>

        <Form.Item name="description" label="Описание">
          <Input.TextArea rows={2} placeholder="Краткое описание назначения шаблона" />
        </Form.Item>

        <Form.Item
          label={
            <Space>
              <span>Содержимое (JSON)</span>
              <Tooltip title={<pre style={{ fontSize: 12, margin: 0 }}>{VARIABLE_HINT}</pre>}>
                <InfoCircleOutlined style={{ color: '#1677ff', cursor: 'pointer' }} />
              </Tooltip>
            </Space>
          }
          validateStatus={jsonError ? 'error' : jsonStr ? 'success' : ''}
          help={jsonError}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <TextArea
              value={jsonStr}
              onChange={handleJsonChange}
              rows={16}
              style={{ fontFamily: 'monospace', fontSize: 13 }}
              spellCheck={false}
            />
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Text type="secondary" style={{ fontSize: 12 }}>
                Используйте <code>{'{{variable}}'}</code> для подстановки значений при применении
              </Text>
              <Button
                size="small"
                icon={<FormatPainterOutlined />}
                onClick={handleFormat}
                disabled={!!jsonError}
              >
                Форматировать
              </Button>
            </div>
          </div>
        </Form.Item>
      </Form>

      {!jsonError && (
        <Alert
          type="info"
          showIcon
          style={{ marginTop: 8 }}
          message="Переменные устройства подставляются автоматически при применении шаблона к каждому устройству."
        />
      )}
    </Modal>
  );
}
