// Человекочитаемые подписи и статусы для событий журнала (event_log).

const EVENT_LABELS: Record<string, string> = {
  DEVICE_ADDED: 'Добавление устройства',
  DEVICE_UPDATED: 'Изменение устройства',
  DEVICE_DELETED: 'Удаление устройства',
  CONFIG_APPLIED: 'Применение конфигурации',
  CONFIG_APPLY_STARTED: 'Запуск применения конфигурации',
  CONFIG_ROLLED_BACK: 'Откат конфигурации',
  LOGIN_SUCCESS: 'Вход в приложение',
  LOGIN_FAILURE: 'Неудачный вход',
  LOGOUT: 'Выход из приложения',
};

export const eventActionLabel = (eventType?: string): string => {
  if (!eventType) return '—';
  return EVENT_LABELS[eventType] || eventType.replace(/_/g, ' ').toLowerCase();
};

export type EventResult = 'Успех' | 'Ошибка';

export const eventResult = (eventType?: string): EventResult => {
  const t = (eventType || '').toUpperCase();
  return t.includes('FAIL') || t.includes('ERROR') ? 'Ошибка' : 'Успех';
};

export const eventResultColor = (result: EventResult): string =>
  result === 'Успех' ? 'success' : 'error';
