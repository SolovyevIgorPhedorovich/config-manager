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
  // Добавленные поля
  operatingSystem?: 'linux' | 'windows'; // ОС для ПК и VM
  manufacturer?: string; // Производитель для МФУ
  model?: string; // Модель устройства
  connectionProfile?: ConnectionProfile; // Профиль подключения
}

export interface ConnectionProfile {
  mode: 'ssh_linux' | 'ssh_cisco' | 'winrm' | 'snmp';
  host: string;
  port: number;
  username?: string;
  authType?: 'password' | 'key';
  secret?: string;
  domain?: string;
  community?: string;
  snmpVersion?: 'v2c' | 'v3';
  isHiddenConnection?: boolean;
}

export interface ScanOption {
  ip: string;
  mask: number;
  port: number;
  community: CommunityVersion;
  snmpv: string;
}

export enum ConfigTypeCode {
  WINDOWS_SETTINGS = 0,
  PROXMOX_VM_CONF = 1,
  CISCO_RUNNING_CONFIG = 2,
  LINUX_CONFIG = 3,      // Добавлено для Linux
  MFU_CONFIG = 4,        // Добавлено для МФУ
  SNMP_CONFIG = 5        // Добавлено для SNMP
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
  createdBy?: string;     // Кто создал версию
  description?: string;    // Описание изменений
}

export interface AuditLog {
  id?: number;
  userId?: string;
  userName?: string;       // Добавлено: имя пользователя
  actionType: string;
  targetDeviceId?: number;
  targetDeviceName?: string; // Добавлено: имя устройства
  oldConfig?: string;
  newConfig?: string;
  status: 'QUEUED' | 'RUNNING' | 'SUCCESS' | 'FAILED';
  createdAt?: string;
  completedAt?: string;    // Добавлено: время завершения
  errorMessage?: string;   // Добавлено: сообщение об ошибке
  ipAddress?: string;      // Добавлено: IP адрес пользователя
}

export interface Settings {
  apiUrl: string;
  autoRefreshInterval: number;
  enableNotifications: boolean;
  adEnabled: boolean;
  adUrl: string;
  adBaseDn: string;
  adUserSearchFilter: string;
  // Добавленные настройки
  defaultSNMPCommunity?: string;    // SNMP community по умолчанию
  sshTimeout?: number;               // Таймаут SSH подключения (сек)
  winrmTimeout?: number;             // Таймаут WinRM подключения (сек)
  maxConcurrentScans?: number;       // Максимум параллельных сканирований
  auditRetentionDays?: number;       // Срок хранения аудит-логов (дней)
}

// Дополнительные типы для удобства
export interface DeviceWithStatus extends Device {
  status?: 'online' | 'offline' | 'error';
  lastSeen?: string;
  lastSync?: string;
}

export interface ScanTask {
  id: string;
  status: 'pending' | 'running' | 'completed' | 'failed';
  progress: number;
  startTime: string;
  endTime?: string;
  totalDevices?: number;
  foundDevices?: Device[];
  error?: string;
}

export interface BulkOperation {
  id: string;
  deviceIds: number[];
  operationType: 'apply_config' | 'reboot' | 'backup' | 'update_credentials';
  status: 'pending' | 'running' | 'completed' | 'failed';
  progress: number;
  results: Array<{
    deviceId: number;
    status: 'success' | 'failed';
    message?: string;
  }>;
  createdAt: string;
  completedAt?: string;
}

export interface DeviceTemplate {
  id?: number;
  name: string;
  description?: string;
  deviceType: DeviceTypeCode;
  configTemplate: string;
  variables: Record<string, string | number | boolean>;
  createdAt?: string;
  updatedAt?: string;
}

// Типы для ответов API
export interface ApiResponse<T = any> {
  success: boolean;
  data?: T;
  error?: string;
  message?: string;
}

export interface PaginatedResponse<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

// Типы для фильтрации устройств
export interface DeviceFilter {
  typeCode?: DeviceTypeCode;
  operatingSystem?: 'linux' | 'windows';
  manufacturer?: string;
  groupName?: string;
  isActive?: boolean;
  search?: string;
  status?: 'online' | 'offline' | 'error';
}

// Типы для статистики
export interface DashboardStats {
  totalDevices: number;
  onlineDevices: number;
  offlineDevices: number;
  devicesByType: Record<DeviceTypeCode, number>;
  devicesByOS: Record<string, number>;
  recentAudits: AuditLog[];
  pendingTasks: number;
}

// Типы для уведомлений
export interface Notification {
  id: string;
  type: 'info' | 'success' | 'warning' | 'error';
  title: string;
  message: string;
  timestamp: Date;
  read: boolean;
  action?: {
    label: string;
    onClick: () => void;
  };
}