import { apiClient } from '../client';
import { Device, ConfigVersion } from '../types';

export interface ScanParams {
  ipaddr: string;
  mask: number;
  port: number;
  community: string;
  snmpv: string;
}

export const devicesApi = {
  getAll: () => apiClient.get<Device[]>('/v1/devices'),
  
  getById: (id: number) => apiClient.get<Device>(`v1/devices/${id}`),
  
  add: (device: Partial<Device>) => apiClient.post<Device>('v1/devices', device),

  delete: (id: number) => 
    apiClient.delete(`/v1/devices/${id}`),

  bulkDelete: (ids: number[]) => 
    apiClient.delete('/v1/devices/bulk', { data: { ids } }),

  deployConfig: (deviceId: number, configType: number, configJson: string) =>
    apiClient.put(`/v1/devices/${deviceId}/deploy`, {
      configType,
      newConfig: configJson
    }),

  getHistory: (deviceId: number) =>
    apiClient.get<ConfigVersion[]>(`/v1/devices/${deviceId}/config/history`),

  startScan: (params: ScanParams) =>
  apiClient.get('/v1/devices/scan', { params }).then(r => r.data),

  getScanStatus: (taskId: string) =>
  apiClient.get('/v1/devices/scan/status', { params: { taskId } }).then(r => r.data),

  terminalSession: (deviceId: number, payload: {
    host: string;
    port: number;
    user: string;
    password?: string;
  }) =>
    apiClient.post<{ sessionId: string }>(
      `/v1/devices/${deviceId}/terminal/session`,
      payload
    ),
};