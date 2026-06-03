import React, { useEffect, useMemo, useState } from 'react';
import {
  Table, Button, Input, Space, Tag, Popconfirm, Tabs, Typography,
  Empty, Tooltip, Badge, message, Descriptions, Card,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  PlusOutlined, EditOutlined, DeleteOutlined, LinkOutlined,
  ThunderboltOutlined, DisconnectOutlined, SearchOutlined,
  FileTextOutlined, CalendarOutlined,
} from '@ant-design/icons';

import { templatesApi, Template, TemplateAssignment } from '../api/templatesApi';
import TemplateFormModal from '../components/TemplateFormModal';
import TemplateAssignModal from '../components/TemplateAssignModal';
import TemplateApplyModal from '../components/TemplateApplyModal';

const { Text, Title } = Typography;

export default function TemplatesPage() {
  const [templates, setTemplates] = useState<Template[]>([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');

  const [activeTab, setActiveTab] = useState('templates');
  const [selectedTemplate, setSelectedTemplate] = useState<Template | null>(null);
  const [assignments, setAssignments] = useState<TemplateAssignment[]>([]);
  const [assignmentsLoading, setAssignmentsLoading] = useState(false);

  const [formOpen, setFormOpen] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState<Template | undefined>();
  const [assignOpen, setAssignOpen] = useState(false);
  const [applyOpen, setApplyOpen] = useState(false);

  // ── Data loading ──────────────────────────────────────────────────────────

  useEffect(() => { loadTemplates(); }, []);

  const loadTemplates = async () => {
    setLoading(true);
    try {
      setTemplates(await templatesApi.getAll());
    } catch {
      message.error('Не удалось загрузить шаблоны');
    } finally {
      setLoading(false);
    }
  };

  const loadAssignments = async (t: Template) => {
    setSelectedTemplate(t);
    setAssignmentsLoading(true);
    setActiveTab('assignments');
    try {
      setAssignments(await templatesApi.getAssignments(t.id));
    } catch {
      message.error('Не удалось загрузить назначения');
    } finally {
      setAssignmentsLoading(false);
    }
  };

  // ── Handlers ──────────────────────────────────────────────────────────────

  const handleCreate = () => { setEditingTemplate(undefined); setFormOpen(true); };

  const handleEdit = (t: Template) => { setEditingTemplate(t); setFormOpen(true); };

  const handleDelete = async (id: number) => {
    try {
      await templatesApi.deactivate(id);
      message.success('Шаблон деактивирован');
      setTemplates(prev => prev.filter(t => t.id !== id));
      if (selectedTemplate?.id === id) setSelectedTemplate(null);
    } catch (e: any) {
      message.error(e?.response?.data?.message || 'Ошибка удаления');
    }
  };

  const handleFormSuccess = (saved: Template) => {
    if (editingTemplate) {
      setTemplates(prev => prev.map(t => t.id === saved.id ? saved : t));
      if (selectedTemplate?.id === saved.id) setSelectedTemplate(saved);
    } else {
      setTemplates(prev => [saved, ...prev]);
    }
    setFormOpen(false);
    message.success(editingTemplate ? 'Шаблон обновлён' : 'Шаблон создан');
  };

  const handleAssignSuccess = (added: TemplateAssignment[]) => {
    setAssignments(prev => [...prev, ...added]);
    setTemplates(prev =>
      prev.map(t =>
        t.id === selectedTemplate?.id
          ? { ...t, deviceCount: t.deviceCount + added.length }
          : t
      )
    );
    setAssignOpen(false);
  };

  const handleUnassign = async (templateId: number, deviceId: number, hostname: string) => {
    try {
      await templatesApi.unassign(templateId, deviceId);
      setAssignments(prev => prev.filter(a => a.deviceId !== deviceId));
      setTemplates(prev =>
        prev.map(t => t.id === templateId ? { ...t, deviceCount: t.deviceCount - 1 } : t)
      );
      message.success(`Устройство ${hostname} отвязано`);
    } catch (e: any) {
      message.error(e?.response?.data?.message || 'Ошибка при отвязке');
    }
  };

  const handleApplySuccess = (batchId: string) => {
    setApplyOpen(false);
  };

  // ── Filtering ─────────────────────────────────────────────────────────────

  const filtered = useMemo(() => {
    if (!search.trim()) return templates;
    const q = search.toLowerCase();
    return templates.filter(
      t => t.name.toLowerCase().includes(q) || t.description?.toLowerCase().includes(q)
    );
  }, [templates, search]);

  // ── Columns ───────────────────────────────────────────────────────────────

  const templateColumns: ColumnsType<Template> = [
    {
      title: 'Название',
      dataIndex: 'name',
      sorter: (a, b) => a.name.localeCompare(b.name),
      render: (name, record) => (
        <Button
          type="link"
          style={{ padding: 0, fontWeight: 600 }}
          onClick={() => loadAssignments(record)}
          icon={<FileTextOutlined />}
        >
          {name}
        </Button>
      ),
    },
    {
      title: 'Описание',
      dataIndex: 'description',
      ellipsis: true,
      render: d => d ? <Text type="secondary">{d}</Text> : <Text type="secondary">—</Text>,
    },
    {
      title: 'Устройств',
      dataIndex: 'deviceCount',
      width: 110,
      align: 'center',
      sorter: (a, b) => a.deviceCount - b.deviceCount,
      render: (n) => (
        <Badge
          count={n}
          showZero
          style={{ backgroundColor: n > 0 ? '#1677ff' : '#d9d9d9' }}
        />
      ),
    },
    {
      title: 'Создал',
      dataIndex: 'createdBy',
      width: 130,
      render: u => u ?? <Text type="secondary">—</Text>,
    },
    {
      title: 'Создан',
      dataIndex: 'createdAt',
      width: 140,
      render: d => d ? new Date(d).toLocaleDateString('ru-RU') : '—',
    },
    {
      title: 'Действия',
      key: 'actions',
      width: 200,
      render: (_, record) => (
        <Space size={4}>
          <Tooltip title="Назначить устройствам">
            <Button
              size="small"
              icon={<LinkOutlined />}
              onClick={() => { setSelectedTemplate(record); loadAssignments(record); setAssignOpen(true); }}
            />
          </Tooltip>
          <Tooltip title="Применить">
            <Button
              size="small"
              type="primary"
              icon={<ThunderboltOutlined />}
              onClick={() => { setSelectedTemplate(record); loadAssignments(record); setApplyOpen(true); }}
              disabled={record.deviceCount === 0}
            />
          </Tooltip>
          <Tooltip title="Редактировать">
            <Button size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)} />
          </Tooltip>
          <Popconfirm
            title="Деактивировать шаблон?"
            description="Шаблон будет скрыт из списка. Назначения сохранятся."
            onConfirm={() => handleDelete(record.id)}
            okText="Да" cancelText="Нет"
          >
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const assignmentColumns: ColumnsType<TemplateAssignment> = [
    {
      title: 'Устройство',
      dataIndex: 'deviceHostname',
      render: (h, r) => (
        <Space>
          <Text strong>{h}</Text>
          {r.deviceIp && <Tag color="default">{r.deviceIp}</Tag>}
        </Space>
      ),
    },
    {
      title: 'Привязал',
      dataIndex: 'assignedBy',
      width: 140,
      render: u => u ?? <Text type="secondary">—</Text>,
    },
    {
      title: 'Дата привязки',
      dataIndex: 'assignedAt',
      width: 160,
      render: d => d
        ? new Date(d).toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' })
        : '—',
    },
    {
      title: '',
      key: 'unassign',
      width: 110,
      render: (_, record) => (
        <Popconfirm
          title={`Отвязать ${record.deviceHostname}?`}
          onConfirm={() => handleUnassign(record.templateId, record.deviceId, record.deviceHostname)}
          okText="Да" cancelText="Нет"
        >
          <Button size="small" danger icon={<DisconnectOutlined />}>Отвязать</Button>
        </Popconfirm>
      ),
    },
  ];

  // ── Render ────────────────────────────────────────────────────────────────

  const tabItems = [
    {
      key: 'templates',
      label: <span><FileTextOutlined /> Шаблоны ({templates.length})</span>,
      children: (
        <div>
          <Space style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }}>
            <Input
              placeholder="Поиск по названию или описанию…"
              prefix={<SearchOutlined />}
              value={search}
              onChange={e => setSearch(e.target.value)}
              allowClear
              style={{ width: 340 }}
            />
            <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
              Создать шаблон
            </Button>
          </Space>

          <Table
            columns={templateColumns}
            dataSource={filtered}
            rowKey="id"
            loading={loading}
            size="middle"
            pagination={{ pageSize: 10, showSizeChanger: true }}
            expandable={{
              expandedRowRender: (record) => (
                <Card
                  size="small"
                  style={{ background: '#f6f8ff', border: '1px solid #e0e7ff' }}
                >
                  <pre style={{
                    fontFamily: 'monospace',
                    fontSize: 12,
                    margin: 0,
                    maxHeight: 200,
                    overflow: 'auto',
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-all',
                  }}>
                    {JSON.stringify(record.content, null, 2)}
                  </pre>
                </Card>
              ),
              rowExpandable: () => true,
            }}
          />
        </div>
      ),
    },
    {
      key: 'assignments',
      label: (
        <span>
          <LinkOutlined />{' '}
          Назначения
          {selectedTemplate && (
            <Tag color="blue" style={{ marginLeft: 6 }}>{selectedTemplate.name}</Tag>
          )}
        </span>
      ),
      children: !selectedTemplate ? (
        <Empty
          description="Выберите шаблон в списке — нажмите на его название или кнопку «Назначить»"
          style={{ padding: '48px 0' }}
        />
      ) : (
        <div>
          <Space style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }}>
            <Space>
              <Text strong style={{ fontSize: 15 }}>
                {selectedTemplate.name}
              </Text>
              {selectedTemplate.description && (
                <Text type="secondary">— {selectedTemplate.description}</Text>
              )}
            </Space>
            <Space>
              <Button
                icon={<ThunderboltOutlined />}
                type="primary"
                disabled={assignments.length === 0}
                onClick={() => setApplyOpen(true)}
              >
                Применить шаблон
              </Button>
              <Button icon={<LinkOutlined />} onClick={() => setAssignOpen(true)}>
                Привязать устройства
              </Button>
            </Space>
          </Space>

          <Table
            columns={assignmentColumns}
            dataSource={assignments}
            rowKey="assignmentId"
            loading={assignmentsLoading}
            size="middle"
            pagination={{ pageSize: 10 }}
            locale={{ emptyText: 'Нет привязанных устройств' }}
          />
        </div>
      ),
    },
  ];

  return (
    <div>
      <Title level={4} style={{ marginBottom: 20 }}>
        <FileTextOutlined style={{ marginRight: 8 }} />
        Шаблоны конфигураций
      </Title>

      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={tabItems}
        style={{ background: '#fff', padding: '0 16px 16px', borderRadius: 8, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}
      />

      <TemplateFormModal
        open={formOpen}
        template={editingTemplate}
        onSuccess={handleFormSuccess}
        onClose={() => setFormOpen(false)}
      />

      <TemplateAssignModal
        open={assignOpen}
        template={selectedTemplate}
        existingAssignments={assignments}
        onSuccess={handleAssignSuccess}
        onClose={() => setAssignOpen(false)}
      />

      <TemplateApplyModal
        open={applyOpen}
        template={selectedTemplate}
        assignments={assignments}
        onSuccess={handleApplySuccess}
        onClose={() => setApplyOpen(false)}
      />
    </div>
  );
}
