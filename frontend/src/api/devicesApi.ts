import { apiClient } from '../client';
import { Device, ConfigVersion, ScanSchedule, ScanCredential, ScanCredentialRequest } from '../types';

export interface ScanParams {
  ipaddr: string;
  mask: number;
  port: number;
  community: string;
  snmpv: string;
  scanMode?: string;
  // SNMPv3 (USM): логин + пароли аутентификации/шифрования
  securityName?: string;
  authProtocol?: string;
  authPassword?: string;
  privProtocol?: string;
  privPassword?: string;
  // Креды для SSH/WinRM-опроса: либо профиль (credentialId), либо разовый ввод
  credentialId?: number;
  sshUsername?: string;
  sshPassword?: string;
  winrmUsername?: string;
  winrmPassword?: string;
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
      newConfig: configJson,
    }),

  getHistory: (deviceId: number) =>
    apiClient.get<ConfigVersion[]>(`/v1/devices/${deviceId}/config/history`),

  inventory: (deviceId: number, creds?: {
    snmpPort?: number;
    community?: string;
    snmpVersion?: string;
    sshUsername?: string;
    sshPassword?: string;
    winrmUsername?: string;
    winrmPassword?: string;
  }) =>
    apiClient.post<{
      success: boolean;
      updated?: boolean;
      detectionMethod?: string;
      sysDescr?: string;
      message?: string;
      device?: Device;
    }>(`/v1/devices/${deviceId}/inventory`, creds ?? {}).then(r => r.data),

  startScan: (params: ScanParams) =>
    apiClient.get('/v1/devices/scan', { params }).then(r => r.data),

  getScanStatus: (taskId: string) =>
    apiClient.get('/v1/devices/scan/status', { params: { taskId } }).then(r => r.data),

  scanSchedules: {
    getAll: () => apiClient.get<ScanSchedule[]>('/v1/devices/scan-schedules').then(r => r.data),
    create: (data: Omit<ScanSchedule, 'id' | 'lastRunAt' | 'lastRunStatus' | 'createdAt'>) =>
      apiClient.post<ScanSchedule>('/v1/devices/scan-schedules', data).then(r => r.data),
    update: (id: number, data: Omit<ScanSchedule, 'id' | 'lastRunAt' | 'lastRunStatus' | 'createdAt'>) =>
      apiClient.put<ScanSchedule>(`/v1/devices/scan-schedules/${id}`, data).then(r => r.data),
    delete: (id: number) => apiClient.delete(`/v1/devices/scan-schedules/${id}`),
    runNow: (id: number) => apiClient.post(`/v1/devices/scan-schedules/${id}/run`).then(r => r.data),
  },

  // Профили доступа (SSH/WinRM) для сканирования — пароли хранятся зашифрованно
  scanCredentials: {
    getAll: () => apiClient.get<ScanCredential[]>('/v1/scan-credentials').then(r => r.data),
    create: (data: ScanCredentialRequest) =>
      apiClient.post<ScanCredential>('/v1/scan-credentials', data).then(r => r.data),
    update: (id: number, data: ScanCredentialRequest) =>
      apiClient.put<ScanCredential>(`/v1/scan-credentials/${id}`, data).then(r => r.data),
    delete: (id: number) => apiClient.delete(`/v1/scan-credentials/${id}`),
  },

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
