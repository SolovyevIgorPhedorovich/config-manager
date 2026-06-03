import { apiClient } from '../client';

export interface Template {
  id: number;
  name: string;
  description?: string;
  content: Record<string, any>;
  isActive: boolean;
  createdBy?: string;
  createdAt?: string;
  deviceCount: number;
}

export interface TemplateAssignment {
  assignmentId: number;
  templateId: number;
  templateName: string;
  deviceId: number;
  deviceHostname: string;
  deviceIp?: string;
  assignedBy?: string;
  assignedAt: string;
}

export interface TemplateCredentials {
  username: string;
  password: string;
  port?: number;
}

export interface TemplateApplyPayload {
  deviceIds?: number[];
  variables?: Record<string, string>;
  credentials: Record<number, TemplateCredentials>;
}

export interface ApplyBatchResponse {
  batchId: string;
  taskGroupIds: string[];
}

export const templatesApi = {
  getAll: () =>
    apiClient.get<Template[]>('/v1/templates').then(r => r.data),

  getById: (id: number) =>
    apiClient.get<Template>(`/v1/templates/${id}`).then(r => r.data),

  create: (data: { name: string; description?: string; content: Record<string, any> }) =>
    apiClient.post<Template>('/v1/templates', data).then(r => r.data),

  update: (id: number, data: { name: string; description?: string; content: Record<string, any> }) =>
    apiClient.put<Template>(`/v1/templates/${id}`, data).then(r => r.data),

  deactivate: (id: number) =>
    apiClient.delete(`/v1/templates/${id}`),

  getAssignments: (id: number) =>
    apiClient.get<TemplateAssignment[]>(`/v1/templates/${id}/devices`).then(r => r.data),

  assign: (id: number, deviceIds: number[]) =>
    apiClient.post<TemplateAssignment[]>(`/v1/templates/${id}/devices`, { deviceIds }).then(r => r.data),

  unassign: (templateId: number, deviceId: number) =>
    apiClient.delete(`/v1/templates/${templateId}/devices/${deviceId}`),

  apply: (id: number, payload: TemplateApplyPayload) =>
    apiClient.post<ApplyBatchResponse>(`/v1/templates/${id}/apply`, payload).then(r => r.data),

  getDeviceTemplates: (deviceId: number) =>
    apiClient.get<TemplateAssignment[]>(`/v1/devices/${deviceId}/templates`).then(r => r.data),
};
