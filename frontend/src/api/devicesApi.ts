import { apiClient } from '../client';
import { Device, ConfigVersion } from '../types';

export const devicesApi = {
  getAll: () => apiClient.get<Device[]>('/devices'),
  
  getById: (id: number) => apiClient.get<Device>(`/devices/${id}`),
  
  add: (device: Partial<Device>) => apiClient.post<Device>('/devices', device),

  deployConfig: (deviceId: number, configType: number, configJson: string) =>
    apiClient.put(`/devices/${deviceId}/deploy`, {
      configType,
      newConfig: configJson
    }),

  getHistory: (deviceId: number) =>
    apiClient.get<ConfigVersion[]>(`/devices/${deviceId}/history`)
};