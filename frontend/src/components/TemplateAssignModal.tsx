import React, { useEffect, useState } from 'react';
import { Modal, Select, Button, Space, Tag, Spin, message } from 'antd';
import { templatesApi, Template, TemplateAssignment } from '../api/templatesApi';
import { devicesApi } from '../api/devicesApi';
import { Device } from '../types';

interface Props {
  open: boolean;
  template: Template | null;
  existingAssignments: TemplateAssignment[];
  onSuccess: (added: TemplateAssignment[]) => void;
  onClose: () => void;
}

export default function TemplateAssignModal({
  open, template, existingAssignments, onSuccess, onClose,
}: Props) {
  const [allDevices, setAllDevices] = useState<Device[]>([]);
  const [selected, setSelected] = useState<number[]>([]);
  const [loading, setLoading] = useState(false);
  const [devicesLoading, setDevicesLoading] = useState(false);

  const assignedIds = new Set(existingAssignments.map(a => a.deviceId));

  useEffect(() => {
    if (open) {
      setSelected([]);
      loadDevices();
    }
  }, [open]);

  const loadDevices = async () => {
    setDevicesLoading(true);
    try {
      const data = await devicesApi.getAll().then(r => r.data);
      setAllDevices(data);
    } catch {
      message.error('Не удалось загрузить список устройств');
    } finally {
      setDevicesLoading(false);
    }
  };

  const handleSubmit = async () => {
    if (!template || selected.length === 0) return;
    setLoading(true);
    try {
      const added = await templatesApi.assign(template.id, selected);
      message.success(`Привязано устройств: ${added.length}`);
      onSuccess(added);
    } catch (e: any) {
      message.error(e?.response?.data?.message || 'Ошибка привязки устройств');
    } finally {
      setLoading(false);
    }
  };

  const availableDevices = allDevices.filter(d => !assignedIds.has(d.id!));

  const options = availableDevices.map(d => ({
    value: d.id!,
    label: `${d.hostname}${d.ips?.[0] ? ` (${d.ips[0]})` : ''}`,
    title: d.type,
  }));

  return (
    <Modal
      title={`Привязать устройства → ${template?.name ?? ''}`}
      open={open}
      onCancel={onClose}
      width={560}
      footer={
        <Space>
          <Button onClick={onClose}>Отмена</Button>
          <Button
            type="primary"
            loading={loading}
            disabled={selected.length === 0}
            onClick={handleSubmit}
          >
            Привязать ({selected.length})
          </Button>
        </Space>
      }
    >
      <Spin spinning={devicesLoading}>
        <div style={{ marginBottom: 8 }}>
          {assignedIds.size > 0 && (
            <div style={{ marginBottom: 12 }}>
              <span style={{ color: '#888', fontSize: 13 }}>
                Уже привязано: {assignedIds.size} устр.{' '}
              </span>
              {existingAssignments.slice(0, 4).map(a => (
                <Tag key={a.deviceId} color="blue">{a.deviceHostname}</Tag>
              ))}
              {existingAssignments.length > 4 && (
                <Tag>+{existingAssignments.length - 4}</Tag>
              )}
            </div>
          )}
        </div>

        <Select
          mode="multiple"
          style={{ width: '100%' }}
          placeholder="Выберите устройства для привязки"
          value={selected}
          onChange={setSelected}
          options={options}
          optionFilterProp="label"
          showSearch
          maxTagCount="responsive"
          notFoundContent={availableDevices.length === 0
            ? 'Все устройства уже привязаны'
            : 'Нет совпадений'}
        />

        {selected.length > 0 && (
          <div style={{ marginTop: 8, color: '#52c41a', fontSize: 13 }}>
            Будет привязано: {selected.length} устр.
          </div>
        )}
      </Spin>
    </Modal>
  );
}
