import { apiClient } from '../client';
import { EventLog } from '../types';

export const eventApi = {
  getLogs: (params?: { deviceId?: number; deviceIds?: number[]; page?: number; size?: number }) =>
    apiClient.get<EventLog[]>('/v1/event/logs', {
      params,
      paramsSerializer: { indexes: null },
    }),
};
