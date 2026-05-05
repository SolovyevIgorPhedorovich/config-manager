export type DeviceTypeCode = 0 | 1 | 2 | 3;
export type CommunityVersion = 0 | 1 | 2;

export interface Device {
  id?: number;
  hostname: string;
  ips: string[];
  typeCode: DeviceTypeCode;
  type: string;
  groupName: string;
  osVersion?: string;
  isActive: boolean;
  createdAt?: string;
}

export interface ScanOption {
  ip: string;
  mask: number
  port: number;
  community: CommunityVersion;
  snmpv: string;
}

export enum ConfigTypeCode {
  WINDOWS_SETTINGS = 0,
  PROXMOX_VM_CONF = 1,
  CISCO_RUNNING_CONFIG = 2
}

export interface ConfigVersion {
  id?: number;
  deviceId: number;
  configType: ConfigTypeCode;
  versionNumber: number;
  appliedAt?: string;
  oldConfigJson?: string;
  newConfig: string;
  diffHash?: string;
  rollbackAvailable: boolean;
}

export interface AuditLog {
  id?: number;
  userId?: string;
  actionType: string;
  targetDeviceId?: number;
  oldConfig?: string;
  newConfig?: string;
  status: 'QUEUED' | 'RUNNING' | 'SUCCESS' | 'FAILED';
  createdAt?: string;
}