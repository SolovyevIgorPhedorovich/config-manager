import React, { useEffect, useRef } from 'react';
import { notification } from 'antd';
import {
  DatabaseOutlined,
  CloudServerOutlined,
  HddOutlined,
  DisconnectOutlined,
} from '@ant-design/icons';
import { useSystemHealth, ServiceStatus } from '../hooks/useSystemHealth';

function formatBytes(bytes: number): string {
  if (bytes >= 1_073_741_824) return `${(bytes / 1_073_741_824).toFixed(1)} ГБ`;
  if (bytes >= 1_048_576)     return `${(bytes / 1_048_576).toFixed(0)} МБ`;
  return `${Math.round(bytes / 1024)} КБ`;
}

const NOTICE_PREFIX = 'system-health';

function statusSuffix(status: ServiceStatus): string {
  return status === 'down' ? 'недоступен'
       : status === 'unreachable' ? 'нет связи'
       : 'неизвестно';
}

function statusColor(status: ServiceStatus): string {
  return status === 'down' ? '#ff4d4f'
       : status === 'unreachable' ? '#fa541c'
       : '#faad14';
}

interface Notice {
  key: string;
  label: string;
  icon: React.ReactNode;
  color: string;
  description?: React.ReactNode;
}

/**
 * Показывает критические ошибки инфраструктуры (сервер/PostgreSQL/Redis/диск)
 * отдельными сообщениями в правом нижнем углу. Сообщение само закрывается,
 * когда соответствующий сервис снова в порядке.
 */
function SystemHealthBanner() {
  const health = useSystemHealth();
  const [api, contextHolder] = notification.useNotification();
  const openedKeys = useRef<Set<string>>(new Set());

  useEffect(() => {
    const checkedAt = health.lastChecked?.toLocaleTimeString('ru-RU');
    const footer = checkedAt
      ? <div style={{ color: '#888', fontSize: 12, marginTop: 4 }}>проверено в {checkedAt}</div>
      : null;

    const notices: Notice[] = [];

    if (!health.serverReachable) {
      // Сервер недоступен — состояние сервисов неизвестно, показываем одно сообщение
      notices.push({
        key: `${NOTICE_PREFIX}:server`,
        label: 'Сервер недоступен',
        icon: <DisconnectOutlined style={{ color: '#ff4d4f' }} />,
        color: '#ff4d4f',
      });
    } else {
      if (health.db !== 'up') {
        notices.push({
          key: `${NOTICE_PREFIX}:db`,
          label: `PostgreSQL: ${statusSuffix(health.db)}`,
          icon: <DatabaseOutlined style={{ color: statusColor(health.db) }} />,
          color: statusColor(health.db),
        });
      }
      if (health.redis !== 'up') {
        notices.push({
          key: `${NOTICE_PREFIX}:redis`,
          label: `Redis: ${statusSuffix(health.redis)}`,
          icon: <CloudServerOutlined style={{ color: statusColor(health.redis) }} />,
          color: statusColor(health.redis),
        });
      }
      if (health.diskSpace !== 'up') {
        notices.push({
          key: `${NOTICE_PREFIX}:disk`,
          label: `Диск: ${statusSuffix(health.diskSpace)}`,
          icon: <HddOutlined style={{ color: statusColor(health.diskSpace) }} />,
          color: statusColor(health.diskSpace),
          description: health.diskFreeBytes !== undefined
            ? `Свободно: ${formatBytes(health.diskFreeBytes)}`
            : undefined,
        });
      }
    }

    const desired = new Set(notices.map((n) => n.key));

    // Закрываем сообщения про сервисы, которые восстановились
    openedKeys.current.forEach((key) => {
      if (!desired.has(key)) api.destroy(key);
    });

    // Открываем/обновляем актуальные сообщения
    notices.forEach((n) => {
      api.open({
        key: n.key,
        message: <span style={{ fontWeight: 600, color: n.color }}>{n.label}</span>,
        description: (
          <>
            {n.description && <div>{n.description}</div>}
            {footer}
          </>
        ),
        icon: n.icon,
        placement: 'bottomRight',
        duration: 0,          // не закрывать автоматически — это критическая ошибка
        closable: true,
      });
    });

    openedKeys.current = desired;
  }, [api, health]);

  // При размонтировании убираем все наши сообщения
  useEffect(() => {
    return () => {
      // читаем актуальный набор ключей в момент очистки
      openedKeys.current.forEach((key) => api.destroy(key));
    };
  }, [api]);

  return contextHolder;
}

export default SystemHealthBanner;
