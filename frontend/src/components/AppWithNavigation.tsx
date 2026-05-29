import React, { useState } from 'react';
import { Avatar, Breadcrumb, Dropdown, Layout, Menu, Space, Tag, Typography } from 'antd';
import {
  HomeOutlined,
  ToolOutlined,
  FolderOpenOutlined,
  LogoutOutlined,
  UserOutlined,
} from '@ant-design/icons';

import { Link, useLocation, useNavigate } from 'react-router-dom';
import DevicesPage from '../pages/DevicesPage';
import AdminPage from '../pages/AdminPage';
import DashboardPage from '../pages/DashboardPage';

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

  const handleMenuClick = ({ key }: { key: string }) => {
    console.log('Меню:', key);
  };

  const handleLogout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    localStorage.removeItem('role');
    navigate('/login', { replace: true });
  };


  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{
        background: 'linear-gradient(90deg, #0f172a 0%, #1d4ed8 100%)',
        padding: '0 24px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        boxShadow: '0 8px 24px rgba(15, 23, 42, 0.18)',
      }}>
        <div style={{ fontSize: '20px', fontWeight: 'bold', color: '#fff' }}>
          🖥️ IT Configuration Manager
        </div>
        <Space size="large">
          <Tag color="processing">Версия 1.0</Tag>
          <Dropdown
            menu={{
              items: [
                { key: 'status', label: `Статус: подключен`, disabled: true },
                { key: 'role', label: `Роль: ${currentRole}`, disabled: true },
                { type: 'divider' },
                { key: 'logout', icon: <LogoutOutlined />, label: 'Выйти из системы', onClick: handleLogout },
              ],
            }}
            trigger={['click']}
          >
            <Space style={{ cursor: 'pointer', color: '#fff' }}>
              <Avatar size="small" icon={<UserOutlined />} />
              <span>{currentUser}</span>
              <Tag color="success">Подключен</Tag>
              <Text style={{ color: '#bfdbfe' }}>{currentRole}</Text>
            </Space>
          </Dropdown>
        </Space>
      </Header>

      <Layout>
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
            onClick={handleMenuClick}
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
              <Menu.Item key="/devices/windows" style={{ paddingLeft: 36 }}>
                <Link to="/devices/windows">
                  <Tag color={deviceColors.windows}>ПК</Tag>
                </Link>
              </Menu.Item>
              <Menu.Item key="/devices/mfu" style={{ paddingLeft: 36 }}>
                <Link to="/devices/mfu">
                  <Tag color={deviceColors.mfu}>МФУ</Tag>
                </Link>
              </Menu.Item>
              <Menu.Item key="/devices/cisco" style={{ paddingLeft: 36 }}>
                <Link to="/devices/cisco">
                  <Tag color={deviceColors.cisco}>Cisco</Tag>
                </Link>
              </Menu.Item>
              <Menu.Item key="/devices/vm" style={{ paddingLeft: 36 }}>
                <Link to="/devices/vm">
                  <Tag color={deviceColors.vm}>VM</Tag>
                </Link>
              </Menu.Item>
            </SubMenu>

            <Menu.Item key="/admin" icon={<ToolOutlined />}>
              <Link to="/admin">Администрирование</Link>
            </Menu.Item>
          </Menu>
        </Sider>

        <Layout style={{ marginLeft: collapsed ? 80 : 260, padding: '0 24px', transition: 'margin-left 0.2s ease' }}>
          <Breadcrumb style={{ margin: '16px 0' }}>
            <Breadcrumb.Item>Главная</Breadcrumb.Item>
            {location.pathname.includes('/devices') && (
              <Breadcrumb.Item>Устройства</Breadcrumb.Item>
            )}
            {location.pathname === '/admin' && (
              <Breadcrumb.Item>Администрирование</Breadcrumb.Item>
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
            {location.pathname === '/admin' && <AdminPage />}
          </Content>
        </Layout>
      </Layout>
    </Layout>
  );
}

export default AppWithNavigation;