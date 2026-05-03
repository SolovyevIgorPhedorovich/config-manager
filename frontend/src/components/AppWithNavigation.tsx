import React, { useState } from 'react';
import { Layout, Menu, theme, Tag, Breadcrumb } from 'antd';
import {
  HomeOutlined,
  DesktopOutlined,
  ToolOutlined,
  FolderOpenOutlined,
} from '@ant-design/icons';

import { Link, useLocation } from 'react-router-dom';
import DevicesPage from '../pages/DevicesPage';
import AdminPage from '../pages/AdminPage';
import DashboardPage from '../pages/DashboardPage';

const { Header, Content, Sider } = Layout;
const { SubMenu } = Menu;

function AppWithNavigation() {
  const [collapsed, setCollapsed] = useState(false);
  const location = useLocation();

  const deviceColors: Record<string, string> = {
    pc: '#1890ff',
    mfu: '#52c41a',
    cisco: '#fa8b0f',
    vm: '#f53f3f',
  };

  const handleMenuClick = ({ key }: { key: string }) => {
    console.log('Меню:', key);
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{
        background: '#fff',
        padding: '0 24px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
      }}>
        <div style={{ fontSize: '20px', fontWeight: 'bold' }}>
          🖥️ IT Configuration Manager
        </div>
        <div style={{ color: '#666' }}>Версия 1.0</div>
      </Header>

      <Layout>
        <Sider
          width={260}
          collapsedWidth={80}
          collapsible
          collapsed={collapsed}
          onCollapse={(value) => setCollapsed(value)}
          style={{
            background: '#001529',
            overflow: 'auto',
            height: '100vh',
            position: 'fixed',
            left: 0,
            top: 64, // высота header
            bottom: 0,
          }}
        >
          <div className="logo" style={{ color: '#fff', textAlign: 'center', padding: '24px 0' }}>
            {collapsed ? 'ITM' : 'Управление ИТ'}
          </div>

          <Menu
            theme="dark"
            mode="inline"
            selectedKeys={[location.pathname]}
            defaultOpenKeys={['devices', 'admin']}
            onClick={handleMenuClick}
            style={{ borderRight: 0 }}
          >

            <Menu.Item key="/" icon={<HomeOutlined />}>
              <Link to="/">🏠 Главная</Link>
            </Menu.Item>

            <SubMenu
              key="devices"
              icon={<FolderOpenOutlined />}
              title="🖥️ Устройства"
              
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
              <Link to="/admin">⚙️ Администрирование</Link>
            </Menu.Item>
          </Menu>
        </Sider>

        <Layout style={{ marginLeft: 260, padding: '0 24px' }}>
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
            background: '#f0f2f5',
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