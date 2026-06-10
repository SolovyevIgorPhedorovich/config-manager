import { apiClient } from '../client';
import type { AxiosResponse } from 'axios';

// Типы для запроса и ответа
export interface ApplyConfigRequest {
  deviceIds: number[];
  configData: Record<string, any>; // или JsonNode в терминах бэкенда
  credentials?: Record<number, DeviceCredentials>; // опционально, если требуется
}

export interface DeviceCredentials {
  username: string;
  password: string;
  port?: number;
}

export interface ConfigApplyStatus {
  status: 'IN_PROGRESS' | 'SUCCESS' | 'FAILED';
  startedAt?: string;
  finishedAt?: string;
  errorMessage?: string;
  progress?: number;
}

export interface ApplyConfigResponse {
  batchId?: string;
  taskGroupIds: string[];
  // Устройства, для которых применение отложено (были офлайн)
  scheduledDeviceIds?: number[];
}

// Заявка отложенного применения (устройство было офлайн)
export interface ScheduledApply {
  id: number;
  deviceId: number;
  deviceHostname?: string;
  configVersionId: number;
  status: 'PENDING' | 'APPLIED' | 'FAILED' | 'CANCELLED';
  source?: string;
  attempts: number;
  lastError?: string;
  createdAt?: string;
  appliedAt?: string;
}

export type DriftResolution = 'ACCEPT_DEVICE' | 'REAPPLY_STORED';

// Фактический статус применения группы задач (config:apply:<groupTaskId>)
export interface ApplyGroupStatus {
  taskGroupId: string;
  status: 'IN_PROGRESS' | 'SUCCESS' | 'FAILED' | 'UNKNOWN';
  deviceId?: number;
  startedAt?: string;
  finishedAt?: string;
  errorMessage?: string;
}

export type ApplyOutcome =
  | { kind: 'success' }
  | { kind: 'failed'; error?: string }
  | { kind: 'scheduled' }
  | { kind: 'pending' };   // не дождались терминального статуса за таймаут

// API методы
export const configApi = {
  /**
   * Отправить конфигурацию на устройства
   */
  applyConfig: (data: ApplyConfigRequest): Promise<AxiosResponse<ApplyConfigResponse>> => {
    return apiClient.post('/v1/devices/configure', data);
  },

  /**
   * Получить статус применения конфигурации для устройства
   */
  getApplyStatus: (deviceId: number): Promise<AxiosResponse<ConfigApplyStatus>> => {
    return apiClient.get(`/v1/devices/${deviceId}/config-apply/status`);
  },

  // ── Очередь отложенного применения (устройство офлайн) ─────────────────────
  getScheduled: (): Promise<ScheduledApply[]> =>
    apiClient.get<ScheduledApply[]>('/v1/config/scheduled').then(r => r.data),

  getDeviceScheduled: (deviceId: number): Promise<ScheduledApply[]> =>
    apiClient.get<ScheduledApply[]>(`/v1/devices/${deviceId}/config/scheduled`).then(r => r.data),

  cancelScheduled: (id: number): Promise<AxiosResponse<void>> =>
    apiClient.delete(`/v1/config/scheduled/${id}`),

  // ── Конфигурация устройства: активная версия, drift, шаблон ────────────────
  // Возвращает активную конфигурацию (или null, если её ещё нет — backend отдаёт 204)
  getActiveConfig: (deviceId: number): Promise<Record<string, any> | null> =>
    apiClient.get<Record<string, any>>(`/v1/devices/${deviceId}/config/active`)
      .then(r => (r.status === 204 ? null : r.data))
      .catch(() => null),

  getConfigAsTemplate: (deviceId: number): Promise<Record<string, any>> =>
    apiClient.get<Record<string, any>>(`/v1/devices/${deviceId}/config/as-template`).then(r => r.data),

  // Сравнение сохранённой (stored) и фактической (actual) конфигураций
  getDrift: (deviceId: number): Promise<{ stored: Record<string, any> | null; actual: Record<string, any> | null }> =>
    apiClient.get(`/v1/devices/${deviceId}/config/drift`).then(r => r.data),

  resolveDrift: (
    deviceId: number,
    resolution: DriftResolution,
    credentials?: DeviceCredentials,
  ): Promise<AxiosResponse<ApplyConfigResponse>> =>
    apiClient.post(`/v1/devices/${deviceId}/config/resolve-drift`, { resolution, credentials }),

  // ── Фактический результат применения ───────────────────────────────────────
  getApplyGroupStatus: (groupTaskId: string): Promise<ApplyGroupStatus> =>
    apiClient.get<ApplyGroupStatus>(`/v1/config/apply/${groupTaskId}/status`).then(r => r.data),

  /** Опрашивает статус группы задач до терминального (SUCCESS/FAILED) или таймаута. */
  waitForApply: async (groupTaskId: string, timeoutMs = 45000): Promise<ApplyGroupStatus> => {
    const start = Date.now();
    let last: ApplyGroupStatus = { taskGroupId: groupTaskId, status: 'UNKNOWN' };
    while (Date.now() - start < timeoutMs) {
      try {
        last = await configApi.getApplyGroupStatus(groupTaskId);
        if (last.status === 'SUCCESS' || last.status === 'FAILED') return last;
      } catch { /* промежуточная ошибка опроса — повторим */ }
      await new Promise(r => setTimeout(r, 1500));
    }
    return last;
  },

  /**
   * Сводит ответ применения к итогу: офлайн → scheduled; иначе дожидается реального
   * результата по первой группе задач (SUCCESS/FAILED), либо pending по таймауту.
   */
  resolveApplyOutcome: async (resp: ApplyConfigResponse): Promise<ApplyOutcome> => {
    if (resp.scheduledDeviceIds?.length) return { kind: 'scheduled' };
    const taskId = resp.taskGroupIds?.[0];
    if (!taskId) return { kind: 'success' }; // нечего применять (нет изменений)
    const status = await configApi.waitForApply(taskId);
    if (status.status === 'SUCCESS') return { kind: 'success' };
    if (status.status === 'FAILED') return { kind: 'failed', error: status.errorMessage };
    return { kind: 'pending' };
  },
};