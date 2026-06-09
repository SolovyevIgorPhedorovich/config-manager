import { apiClient } from '../client';

export interface AdSettings {
  enabled: boolean;
  url: string;
  baseDn: string;
  userDn: string | null;
  passwordSet: boolean;
  userSearchFilter: string;
}

export interface AdSettingsRequest {
  enabled?: boolean;
  url?: string;
  baseDn?: string;
  userDn?: string;
  password?: string;          // пусто = не менять текущий пароль
  userSearchFilter?: string;
}

export const settingsApi = {
  getAd: (): Promise<AdSettings> =>
    apiClient.get<AdSettings>('/v1/settings/ad').then(r => r.data),

  updateAd: (data: AdSettingsRequest): Promise<AdSettings> =>
    apiClient.put<AdSettings>('/v1/settings/ad', data).then(r => r.data),

  testAd: (data: AdSettingsRequest): Promise<{ success: boolean; message?: string; error?: string }> =>
    apiClient.post('/v1/settings/ad/test', data).then(r => r.data),
};
