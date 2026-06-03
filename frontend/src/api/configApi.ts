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
  taskGroupIds: string[];
}

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
};