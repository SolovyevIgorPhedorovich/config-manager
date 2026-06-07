import { apiClient } from '../client';
import { AppUser, AppUserRequest } from '../types';

export const userApi = {
  getAll: () => apiClient.get<AppUser[]>('/v1/users').then(r => r.data),
  getRoles: () => apiClient.get<string[]>('/v1/users/roles').then(r => r.data),
  create: (data: AppUserRequest) =>
    apiClient.post<AppUser>('/v1/users', data).then(r => r.data),
  update: (id: number, data: AppUserRequest) =>
    apiClient.put<AppUser>(`/v1/users/${id}`, data).then(r => r.data),
  delete: (id: number) => apiClient.delete(`/v1/users/${id}`),
};
