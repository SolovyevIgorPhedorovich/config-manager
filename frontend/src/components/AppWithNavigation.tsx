import React, { useState } from 'react';
import { Avatar, Breadcrumb, Dropdown, Layout, Menu, Space, Typography } from 'antd';
import {
  HomeOutlined,
  ToolOutlined,
  FolderOpenOutlined,
  SettingOutlined,
  AuditOutlined,
  LogoutOutlined,
  UserOutlined,
  FileTextOutlined,
} from '@ant-design/icons';

import { Link, useLocation, useNavigate } from 'react-router-dom';
import DevicesPage from '../pages/DevicesPage';
import AdminPage from '../pages/AdminPage';
import DashboardPage from '../pages/DashboardPage';
import AuditPage from '../pages/AuditPage';
import TemplatesPage from '../pages/TemplatesPage';
import SystemHealthBanner from './SystemHealthBanner';
import { authApi } from '../api/authApi';

const { Header, Content, Sider } = Layout;
const { SubMenu } = Menu;
const { Text } = Typography;

function AppWithNavigation() {
  const [collapsed, setCollapsed] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();
  const currentUser = localStorage.getItem('username') || 'admin';
  const currentRole = localStorage.getItem('role') || 'Администратор';

  const deviceColors: Record<string, string> = {
    windows: '#1890ff',
    mfu: '#52c41a',
    cisco: '#fa8b0f',
    vm: '#f53f3f',
  };


  const handleLogout = async () => {
    // Отзываем токены на сервере (чёрный список), затем чистим локальные данные
    await authApi.logout();
    navigate('/login', { replace: true });
  };

  const renderDeviceMenuLabel = (label: string, color: string) => (
    <span style={{
      display: 'flex',
      alignItems: 'center',
      gap: 10,
      color: '#e5e7eb',
      fontWeight: 500,
    }}>
      <span style={{
        width: 8,
        height: 8,
        borderRadius: '50%',
        background: color,
        boxShadow: `0 0 10px ${color}`,
      }} />
      <span>{label}</span>
    </span>
  );


  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{
        background: 'linear-gradient(90deg, #0f172a 0%, #1d4ed8 100%)',
        padding: '0 24px 0 0',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        boxShadow: '0 8px 24px rgba(15, 23, 42, 0.18)',
        left: 0,
        position: 'fixed',
        right: 0,
        top: 0,
        zIndex: 1000,
      }}>
        <div style={{ fontSize: '20px', fontWeight: 'bold', color: '#fff' }}>
          🖥️ IT Configuration Manager
        </div>
        <Dropdown
          menu={{
            items: [
              { key: 'role', label: `Роль: ${currentRole}`, disabled: true },
              { type: 'divider' },
              { key: 'logout', icon: <LogoutOutlined />, label: 'Выйти из системы', onClick: handleLogout },
            ],
          }}
          trigger={['click']}
        >
          <Space style={{ cursor: 'pointer', color: '#fff' }}>
            <Avatar size="small" icon={<UserOutlined />} />
            <span style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.2 }}>
              <span>{currentUser}</span>
              <Text style={{ color: '#bfdbfe', fontSize: 12 }}>{currentRole}</Text>
            </span>
          </Space>
        </Dropdown>
      </Header>

      <SystemHealthBanner />

      <Layout style={{ paddingTop: 64 }}>
        <Sider
          width={260}
          collapsedWidth={80}
          collapsible
          collapsed={collapsed}
          onCollapse={(value) => setCollapsed(value)}
          style={{
            background: 'linear-gradient(180deg, #0f172a 0%, #111827 55%, #1e1b4b 100%)',
            overflow: 'auto',
            height: '100vh',
            position: 'fixed',
            left: 0,
            top: 64, // высота header
            bottom: 0,
          }}
        >
          <div className="logo" style={{ color: '#fff', textAlign: 'center', padding: '24px 12px', fontWeight: 700, letterSpacing: 0.5 }}>
            {collapsed ? 'ITM' : '⚙️ Управление ИТ'}
          </div>

          <Menu
            theme="dark"
            mode="inline"
            selectedKeys={[location.pathname]}
            defaultOpenKeys={['devices', 'admin']}
            style={{ borderRight: 0, background: 'transparent', padding: '0 8px' }}
          >

            <Menu.Item key="/" icon={<HomeOutlined />}>
              <Link to="/">Главная</Link>
            </Menu.Item>

            <SubMenu
              key="devices"
              icon={<FolderOpenOutlined />}
              title="Устройства"
              
            >
              <Menu.Item key="/devices/windows" style={{ margin: '4px 8px', borderRadius: 10, paddingLeft: 32 }}>
                <Link to="/devices/windows">
                  {renderDeviceMenuLabel('ПК', deviceColors.windows)}
                </Link>
              </Menu.Item>
              <Menu.Item key="/devices/mfu" style={{ margin: '4px 8px', borderRadius: 10, paddingLeft: 32 }}>
                <Link to="/devices/mfu">
                  {renderDeviceMenuLabel('МФУ', deviceColors.mfu)}
                </Link>
              </Menu.Item>
              <Menu.Item key="/devices/cisco" style={{ margin: '4px 8px', borderRadius: 10, paddingLeft: 32 }}>
                <Link to="/devices/cisco">
                  {renderDeviceMenuLabel('Cisco', deviceColors.cisco)}
                </Link>
              </Menu.Item>
              <Menu.Item key="/devices/vm" style={{ margin: '4px 8px', borderRadius: 10, paddingLeft: 32 }}>
                <Link to="/devices/vm">
                  {renderDeviceMenuLabel('VM / Proxmox', deviceColors.vm)}
                </Link>
              </Menu.Item>
            </SubMenu>

            <Menu.Item key="/templates" icon={<FileTextOutlined />}>
              <Link to="/templates">Шаблоны</Link>
            </Menu.Item>

            <SubMenu
              key="admin"
              icon={<ToolOutlined />}
              title="Администрирование"
            >
              <Menu.Item key="/admin/settings" icon={<SettingOutlined />} style={{ margin: '4px 8px', borderRadius: 10 }}>
                <Link to="/admin/settings">Настройка</Link>
              </Menu.Item>
              <Menu.Item key="/admin/audit" icon={<AuditOutlined />} style={{ margin: '4px 8px', borderRadius: 10 }}>
                <Link to="/admin/audit">Аудит</Link>
              </Menu.Item>
            </SubMenu>
          </Menu>
        </Sider>

        <Layout style={{ marginLeft: collapsed ? 80 : 260, padding: '0 24px', transition: 'margin-left 0.2s ease' }}>
          <Breadcrumb style={{ margin: '16px 0' }}>
            <Breadcrumb.Item>Главная</Breadcrumb.Item>
            {location.pathname.includes('/devices') && (
              <Breadcrumb.Item>Устройства</Breadcrumb.Item>
            )}
            {location.pathname.startsWith('/admin') && (
              <Breadcrumb.Item>Администрирование</Breadcrumb.Item>
            )}
            {location.pathname === '/admin/settings' && (
              <Breadcrumb.Item>Настройка</Breadcrumb.Item>
            )}
            {location.pathname === '/admin/audit' && (
              <Breadcrumb.Item>Аудит</Breadcrumb.Item>
            )}
            {location.pathname === '/templates' && (
              <Breadcrumb.Item>Шаблоны</Breadcrumb.Item>
            )}
          </Breadcrumb>

          <Content style={{
            background: 'linear-gradient(180deg, #f8fafc 0%, #eef2ff 100%)',
            padding: 24,
            minHeight: 280,
          }}>
            {location.pathname === '/' && <DashboardPage />}
            {location.pathname.startsWith('/devices') && (
              <DevicesPage type={location.pathname.split('/')[2]} />
            )}
            {(location.pathname === '/admin' || location.pathname === '/admin/settings') && <AdminPage />}
            {location.pathname === '/admin/audit' && <AuditPage />}
            {location.pathname === '/templates' && <TemplatesPage />}
          </Content>
        </Layout>
      </Layout>
    </Layout>
  );
}

export default AppWithNavigation;