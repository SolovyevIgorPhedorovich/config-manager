import { apiClient } from '../client';
import { AuditLog } from '../types';

export const auditApi = {
  getLogs: (params?: { deviceId?: number; page?: number; size?: number }) =>
    apiClient.get<AuditLog[]>('/audit/logs', { params }),
};