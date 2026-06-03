import React from 'react';
import { Space, Switch, Typography } from 'antd';
import { CheckCircleOutlined, SaveOutlined, WarningOutlined } from '@ant-design/icons';

const { Text } = Typography;

interface Props {
  value: boolean;
  onChange: (v: boolean) => void;
  onLabel?: string;
  offLabel?: string;
  onDescription?: string;
  offDescription?: string;
}

const DEFAULTS = {
  onLabel:  'Сохранить в постоянную память',
  offLabel: 'Только в оперативную память',
  onDescription:  'Настройки сохраняются постоянно и переживают перезагрузку.',
  offDescription: 'Настройки применяются только к текущему сеансу. Перезагрузка устройства сбросит их — удобно для тестирования.',
};

export default function SaveToMemoryToggle({ value, onChange, onLabel, offLabel, onDescription, offDescription }: Props) {
  return (
    <div style={{
      padding: '14px 16px',
      border: `2px solid ${value ? '#52c41a' : '#faad14'}`,
      borderRadius: 8,
      background: value ? '#f6ffed' : '#fffbe6',
      transition: 'border-color 0.2s, background 0.2s',
    }}>
      <Space align="start" style={{ width: '100%', justifyContent: 'space-between' }}>
        <Space align="start">
          {value
            ? <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 20, marginTop: 2 }} />
            : <WarningOutlined    style={{ color: '#faad14', fontSize: 20, marginTop: 2 }} />
          }
          <div>
            <Text strong style={{ fontSize: 14 }}>
              {value ? (onLabel ?? DEFAULTS.onLabel) : (offLabel ?? DEFAULTS.offLabel)}
            </Text>
            <br />
            <Text type="secondary" style={{ fontSize: 12 }}>
              {value ? (onDescription ?? DEFAULTS.onDescription) : (offDescription ?? DEFAULTS.offDescription)}
            </Text>
          </div>
        </Space>
        <Switch
          checked={value}
          onChange={onChange}
          checkedChildren={<SaveOutlined />}
          unCheckedChildren="RAM"
          style={{ marginLeft: 12, flexShrink: 0 }}
        />
      </Space>
    </div>
  );
}
