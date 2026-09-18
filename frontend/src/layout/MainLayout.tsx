import React, { useState } from 'react';
import { Layout, Menu, Button, Dropdown, Avatar, theme } from 'antd';
import {
  RobotOutlined,
  BookOutlined,
  DeploymentUnitOutlined,
  CompassOutlined,
  SettingOutlined,
  UserOutlined,
  TeamOutlined,
  MenuOutlined,
  LogoutOutlined,
  MoonOutlined,
  SunOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons';
import { useNavigate, useLocation, Outlet } from 'react-router-dom';
import { useAuthStore } from '../store/useAuthStore';
import { useThemeStore } from '../store/useThemeStore';

const { Header, Sider, Content } = Layout;

export const MainLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { userInfo, logout } = useAuthStore();
  const { isDark, toggleTheme } = useThemeStore();

  const menuItems = [
    {
      key: '/chat',
      icon: <RobotOutlined className="text-indigo-400 text-lg" />,
      label: 'AI 智能体工作台',
    },
    {
      key: '/research',
      icon: <CompassOutlined className="text-cyan-400 text-lg" />,
      label: '深度研究工坊 (Graph)',
    },
    {
      key: '/dataset',
      icon: <BookOutlined className="text-emerald-400 text-lg" />,
      label: '知识库管理 (双轨隔离)',
    },
    {
      key: '/agent',
      icon: <DeploymentUnitOutlined className="text-purple-400 text-lg" />,
      label: 'Agent 智能体编排',
    },
    {
      key: '/system',
      icon: <SettingOutlined className="text-slate-400 text-lg" />,
      label: '系统权限管理',
      children: [
        {
          key: '/system/user',
          icon: <UserOutlined />,
          label: '用户管理',
        },
        {
          key: '/system/role',
          icon: <TeamOutlined />,
          label: '角色管理',
        },
        {
          key: '/system/menu',
          icon: <MenuOutlined />,
          label: '菜单权限',
        },
      ],
    },
  ];

  const handleMenuClick = ({ key }: { key: string }) => {
    navigate(key);
  };

  const userMenuItems = [
    {
      key: 'username',
      label: (
        <div className="py-1">
          <div className="font-semibold text-slate-800 dark:text-slate-100">{userInfo?.nickName || '超级管理员'}</div>
          <div className="text-xs text-slate-400">@{userInfo?.username || 'admin'}</div>
        </div>
      ),
      disabled: true,
    },
    { type: 'divider' as const },
    {
      key: 'logout',
      icon: <LogoutOutlined className="text-red-500" />,
      label: <span className="text-red-500">退出登录</span>,
      onClick: logout,
    },
  ];

  return (
    <Layout className="min-h-screen">
      {/* 侧边栏 */}
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        width={256}
        className="border-r border-slate-200 dark:border-slate-800 shadow-sm"
        style={{
          background: isDark ? '#0f172a' : '#ffffff',
        }}
      >
        <div className="h-16 flex items-center px-5 gap-3 border-b border-slate-100 dark:border-slate-800/80">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-indigo-600 via-indigo-500 to-purple-500 flex items-center justify-center shadow-lg shadow-indigo-500/30 text-white font-bold text-lg flex-shrink-0">
            ✦
          </div>
          {!collapsed && (
            <div className="flex flex-col overflow-hidden">
              <span className="font-bold text-slate-800 dark:text-slate-100 tracking-wide text-base leading-tight truncate">
                Antigravity AI
              </span>
              <span className="text-[10px] text-slate-400 font-mono uppercase tracking-wider">
                Graph + RAG 双轨隔离
              </span>
            </div>
          )}
        </div>

        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          defaultOpenKeys={['/system']}
          items={menuItems}
          onClick={handleMenuClick}
          className="border-none mt-2 font-medium"
          style={{
            background: 'transparent',
          }}
        />
      </Sider>

      <Layout>
        <Header
          className="px-6 flex items-center justify-between border-b border-slate-200 dark:border-slate-800 sticky top-0 z-10 glass-effect"
          style={{
            background: isDark ? 'rgba(15, 23, 42, 0.85)' : 'rgba(255, 255, 255, 0.85)',
            height: 64,
          }}
        >
          <div className="flex items-center gap-4">
            <Button
              type="text"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed(!collapsed)}
              className="text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800"
            />
            <div className="text-xs px-2.5 py-1 rounded-full bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-indigo-400 font-medium border border-indigo-200 dark:border-indigo-800/50">
              Spring AI Alibaba Graph · 全栈 PostgreSQL 统一存储
            </div>
          </div>

          <div className="flex items-center gap-3">
            <Button
              type="text"
              icon={isDark ? <SunOutlined className="text-amber-400" /> : <MoonOutlined className="text-slate-600" />}
              onClick={toggleTheme}
              className="hover:bg-slate-100 dark:hover:bg-slate-800"
            />

            <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
              <div className="flex items-center gap-2.5 cursor-pointer py-1 px-2 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors">
                <Avatar
                  size="small"
                  className="bg-indigo-600 font-semibold"
                >
                  {userInfo?.nickName?.charAt(0) || 'A'}
                </Avatar>
                <span className="text-sm font-medium text-slate-700 dark:text-slate-200 hidden sm:inline">
                  {userInfo?.nickName || '超级管理员'}
                </span>
              </div>
            </Dropdown>
          </div>
        </Header>

        <Content
          className={location.pathname === '/chat' ? 'overflow-hidden' : 'p-6 overflow-y-auto'}
          style={{
            background: isDark ? '#090d16' : '#f8fafc',
            height: 'calc(100vh - 64px)',
            display: location.pathname === '/chat' ? 'flex' : 'block',
            flexDirection: location.pathname === '/chat' ? 'column' : undefined,
          }}
        >
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};
