import { useCallback, useEffect, useState } from 'react';
import { fetchHealth, SystemHealth } from '../api/healthApi';

export type ServiceStatus = 'up' | 'down' | 'unknown' | 'unreachable';

export interface HealthState {
  serverReachable: boolean;
  db: ServiceStatus;
  redis: ServiceStatus;
  diskSpace: ServiceStatus;
  diskFreeBytes?: number;
  raw?: SystemHealth;
  lastChecked?: Date;
}

const POLL_INTERVAL_MS = 30_000;

function toStatus(s?: string): ServiceStatus {
  if (!s) return 'unknown';
  return s.toUpperCase() === 'UP' ? 'up' : 'down';
}

export function useSystemHealth(): HealthState {
  const [state, setState] = useState<HealthState>({
    serverReachable: true,
    db: 'unknown',
    redis: 'unknown',
    diskSpace: 'unknown',
  });

  const check = useCallback(async () => {
    try {
      const health = await fetchHealth();
      const diskDetails = health.components?.diskSpace?.details;
      setState({
        serverReachable: true,
        db:        toStatus(health.components?.db?.status),
        redis:     toStatus(health.components?.redis?.status),
        diskSpace: toStatus(health.components?.diskSpace?.status),
        diskFreeBytes: typeof diskDetails?.free === 'number' ? diskDetails.free : undefined,
        raw: health,
        lastChecked: new Date(),
      });
    } catch {
      setState((prev) => ({
        ...prev,
        serverReachable: false,
        db:        'unreachable',
        redis:     'unreachable',
        diskSpace: 'unreachable',
        lastChecked: new Date(),
      }));
    }
  }, []);

  useEffect(() => {
    check();
    const timer = setInterval(check, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [check]);

  return state;
}
