import React, { useState } from 'react';
import { Layout, Menu, Button, Dropdown, Avatar, Breadcrumb, Tag } from 'antd';
import {
  BookOutlined,
  DeploymentUnitOutlined,
  SettingOutlined,
  UserOutlined,
  TeamOutlined,
  MenuOutlined,
  LogoutOutlined,
  MoonOutlined,
  SunOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ArrowLeftOutlined,
  SafetyCertificateOutlined,
  MessageOutlined,
} from '@ant-design/icons';
import { useNavigate, useLocation, Outlet } from 'react-router-dom';
import { useAuthStore } from '../store/useAuthStore';
import { useThemeStore } from '../store/useThemeStore';

const { Header, Sider, Content } = Layout;

export const AdminLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { userInfo, logout } = useAuthStore();
  const { isDark, toggleTheme } = useThemeStore();

  const menuItems = [
    {
      key: '/admin/dataset',
      icon: <BookOutlined className="text-emerald-500 text-base" />,
      label: '知识库中枢 (双轨隔离)',
    },
    {
      key: '/admin/chat-history',
      icon: <MessageOutlined className="text-indigo-500 text-base" />,
      label: '对话记录与审计管理',
    },
    {
      key: '/admin/agent',
      icon: <DeploymentUnitOutlined className="text-purple-500 text-base" />,
      label: 'Agent 智能体编排',
    },
    {
      key: '/admin/system',
      icon: <SettingOutlined className="text-slate-500 text-base" />,
      label: '系统权限管理中心',
      children: [
        {
          key: '/admin/system/user',
          icon: <UserOutlined />,
          label: '用户管理',
        },
        {
          key: '/admin/system/role',
          icon: <TeamOutlined />,
          label: '角色权限',
        },
        {
          key: '/admin/system/menu',
          icon: <MenuOutlined />,
          label: '菜单权限字典',
        },
      ],
    },
  ];

  const handleMenuClick = ({ key }: { key: string }) => {
    navigate(key);
  };

  const getBreadcrumbs = () => {
    const path = location.pathname;
    if (path.includes('/admin/dataset')) return ['管理控制台', '企业知识库'];
    if (path.includes('/admin/chat-history')) return ['管理控制台', '对话记录管理'];
    if (path.includes('/admin/agent')) return ['管理控制台', '智能体编排'];
    if (path.includes('/admin/system/user')) return ['管理控制台', '系统权限', '用户管理'];
    if (path.includes('/admin/system/role')) return ['管理控制台', '系统权限', '角色管理'];
    if (path.includes('/admin/system/menu')) return ['管理控制台', '系统权限', '菜单权限'];
    return ['管理控制台'];
  };

  const userMenuItems = [
    {
      key: 'username',
      label: (
        <div className="py-1 px-1">
          <div className="font-semibold text-slate-800 dark:text-slate-100">{userInfo?.nickName || '超级管理员'}</div>
          <div className="text-xs text-slate-400">@{userInfo?.username || 'admin'}</div>
        </div>
      ),
      disabled: true,
    },
    { type: 'divider' as const },
    {
      key: 'toUser',
      icon: <ArrowLeftOutlined className="text-indigo-500" />,
      label: <span className="font-medium text-indigo-500">返回用户工作台</span>,
      onClick: () => navigate('/chat'),
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
    <Layout className="min-h-screen bg-slate-100 dark:bg-[#0c0f17] text-slate-800 dark:text-slate-100 transition-colors duration-300">
      {/* 经典中后台侧边栏 */}
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        width={250}
        theme={isDark ? 'dark' : 'light'}
        className="border-r border-slate-200 dark:border-slate-800 select-none shadow-sm z-20"
      >
        {/* 控制台 Logo */}
        <div className="h-16 flex items-center px-5 border-b border-slate-200 dark:border-slate-800">
          <div
            className="flex items-center space-x-3 cursor-pointer overflow-hidden"
            onClick={() => navigate('/admin/dataset')}
          >
            <div className="w-8 h-8 rounded-lg bg-indigo-600 flex items-center justify-center shrink-0 shadow-md shadow-indigo-600/30">
              <SafetyCertificateOutlined className="text-white text-base" />
            </div>
            {!collapsed && (
              <div className="leading-none whitespace-nowrap">
                <span className="font-bold text-sm tracking-tight text-slate-900 dark:text-white">
                  KnowledgeOps
                </span>
                <Tag color="purple" className="ml-1.5 text-[10px] px-1 py-0 leading-tight">
                  Admin
                </Tag>
              </div>
            )}
          </div>
        </div>

        {/* 侧边菜单树 */}
        <div className="py-2">
          <Menu
            mode="inline"
            selectedKeys={[location.pathname]}
            defaultOpenKeys={['/admin/system']}
            items={menuItems}
            onClick={handleMenuClick}
            className="border-none text-sm font-medium"
          />
        </div>
      </Sider>

      {/* 右侧主控区 */}
      <Layout className="flex flex-col bg-slate-50 dark:bg-[#0c0f17]">
        {/* 管理端 Header */}
        <Header className="h-16 px-6 bg-white dark:bg-[#121826] border-b border-slate-200 dark:border-slate-800 flex items-center justify-between sticky top-0 z-10 transition-colors">
          <div className="flex items-center space-x-4">
            <Button
              type="text"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed(!collapsed)}
              className="text-base text-slate-600 dark:text-slate-300"
            />
            {/* 面包屑导航 */}
            <Breadcrumb
              items={getBreadcrumbs().map((b) => ({
                title: <span className="text-xs font-medium text-slate-500 dark:text-slate-400">{b}</span>,
              }))}
            />
          </div>

          <div className="flex items-center space-x-3">
            {/* 返回用户端对话工作台 */}
            <Button
              type="primary"
              icon={<ArrowLeftOutlined />}
              onClick={() => navigate('/chat')}
              className="rounded-xl text-xs font-medium bg-gradient-to-r from-indigo-600 to-violet-600 border-none shadow-sm shadow-indigo-500/20"
            >
              返回用户工作台
            </Button>

            {/* 明暗切换 */}
            <Button
              type="text"
              shape="circle"
              icon={isDark ? <SunOutlined className="text-amber-400" /> : <MoonOutlined className="text-slate-500" />}
              onClick={toggleTheme}
            />

            {/* 管理员头像 */}
            <Dropdown menu={{ items: userMenuItems }} placement="bottomRight" arrow trigger={['click']}>
              <div className="flex items-center space-x-2.5 cursor-pointer p-1.5 rounded-xl hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors select-none">
                <Avatar
                  size={32}
                  className="bg-indigo-600 shadow-sm"
                  icon={<UserOutlined />}
                >
                  {userInfo?.nickName?.[0] || 'A'}
                </Avatar>
                <div className="hidden sm:block text-left pr-1">
                  <div className="text-xs font-medium leading-none text-slate-800 dark:text-slate-200">
                    {userInfo?.nickName || '超级管理员'}
                  </div>
                  <div className="text-[10px] text-indigo-500 dark:text-indigo-400 mt-1 leading-none font-semibold">
                    Admin Console
                  </div>
                </div>
              </div>
            </Dropdown>
          </div>
        </Header>

        {/* 管理端页面主体内容区 */}
        <Content className="flex-1 p-6 overflow-y-auto max-h-[calc(100vh-4rem)]">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};
