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
};