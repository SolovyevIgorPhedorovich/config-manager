import { apiClient } from '../client';
import { AuditLog } from '../types';

export const eventApi = {
  getLogs: (params?: { deviceId?: number; page?: number; size?: number }) =>
    apiClient.get<AuditLog[]>('/v1/event/logs', { params }),
};
