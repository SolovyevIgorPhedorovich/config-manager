import axios from 'axios';

export interface ComponentHealth {
  status: string;
  details?: Record<string, unknown>;
}

export interface SystemHealth {
  status: string;
  components?: {
    db?: ComponentHealth;
    redis?: ComponentHealth;
    diskSpace?: ComponentHealth;
    [key: string]: ComponentHealth | undefined;
  };
}

export const fetchHealth = (): Promise<SystemHealth> =>
  axios
    .get<SystemHealth>('/actuator/health', { timeout: 5000 })
    .then((r) => r.data);
