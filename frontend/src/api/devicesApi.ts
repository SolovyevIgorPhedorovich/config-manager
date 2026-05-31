import { apiClient } from '../client';
import { Device, ConfigVersion } from '../types';

export const devicesApi = {
  getAll: () => apiClient.get<Device[]>('v1/devices'),
  
  getById: (id: number) => apiClient.get<Device>(`v1/devices/${id}`),
  
  add: (device: Partial<Device>) => apiClient.post<Device>('v1/devices', device),

  delete: (id: number, user: string) => apiClient.post<Device>(`v1/devices/${id}`),

  deployConfig: (deviceId: number, configType: number, configJson: string) =>
    apiClient.put(`/devices/${deviceId}/deploy`, {
      configType,
      newConfig: configJson
    }),

  getHistory: (deviceId: number) =>
    apiClient.get<ConfigVersion[]>(`v1/devices/${deviceId}/history`)
};