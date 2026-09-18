import React, { useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './store/useAuthStore';
import { UserLayout } from './layout/UserLayout';
import { AdminLayout } from './layout/AdminLayout';
import { LoginPage } from './pages/login';
import { AdminLoginPage } from './pages/login/AdminLoginPage';
import { ChatPage } from './pages/chat';
import { DeepResearchPage } from './pages/research';
import { DatasetPage } from './pages/dataset';
import { ChatHistoryManagePage } from './pages/chat-history';
import { AgentPage } from './pages/agent';
import { UserManagePage } from './pages/system/user';
import { RoleManagePage } from './pages/system/role';
import { MenuManagePage } from './pages/system/menu';

/** 普通用户路由守卫：有 token 即可进入 */
const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const token = localStorage.getItem('token');
  const { userInfo, fetchUserInfo } = useAuthStore();

  useEffect(() => {
    if (token && !userInfo) {
      fetchUserInfo();
    }
  }, [token, userInfo]);

  if (!token) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
};

/** 管理员路由守卫：有 token 且具备 admin 角色才可进入 */
const AdminProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const token = localStorage.getItem('token');
  const { userInfo, roles, fetchUserInfo } = useAuthStore();

  useEffect(() => {
    if (token && !userInfo) {
      fetchUserInfo();
    }
  }, [token, userInfo]);

  if (!token) {
    return <Navigate to="/admin/login" replace />;
  }

  // 用户信息尚未加载完，等待中（避免闪烁跳转）
  if (token && !userInfo) {
    return null;
  }

  // 角色校验：roleKey=admin 或 roleId=1
  const isAdmin = roles?.some((r: any) => r.roleKey === 'admin' || r.roleId === 1) ?? false;
  if (!isAdmin) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
};

export const App: React.FC = () => {
  return (
    <BrowserRouter>
      <Routes>
        {/* ======================================================== */}
        {/* 用户登录页                                                */}
        {/* ======================================================== */}
        <Route path="/login" element={<LoginPage />} />

        {/* ======================================================== */}
        {/* 管理员专属登录页                                          */}
        {/* ======================================================== */}
        <Route path="/admin/login" element={<AdminLoginPage />} />

        {/* ======================================================== */}
        {/* 1. 用户工作台 (User Workplace) - 沉浸式对话工作台         */}
        {/* ======================================================== */}
        <Route
          element={
            <ProtectedRoute>
              <UserLayout />
            </ProtectedRoute>
          }
        >
          <Route path="/" element={<Navigate to="/chat" replace />} />
          <Route path="/chat" element={<ChatPage />} />
          <Route path="/research" element={<DeepResearchPage />} />
        </Route>

        {/* ======================================================== */}
        {/* 2. 管理控制台 (Admin Console) - 仅管理员可访问             */}
        {/* ======================================================== */}
        <Route
          path="/admin"
          element={
            <AdminProtectedRoute>
              <AdminLayout />
            </AdminProtectedRoute>
          }
        >
          <Route index element={<Navigate to="/admin/dataset" replace />} />
          <Route path="dataset" element={<DatasetPage />} />
          <Route path="chat-history" element={<ChatHistoryManagePage />} />
          <Route path="agent" element={<AgentPage />} />
          <Route path="system/user" element={<UserManagePage />} />
          <Route path="system/role" element={<RoleManagePage />} />
          <Route path="system/menu" element={<MenuManagePage />} />
        </Route>

        {/* 兼容旧路由 */}
        <Route path="/dataset" element={<Navigate to="/admin/dataset" replace />} />
        <Route path="/agent" element={<Navigate to="/admin/agent" replace />} />
        <Route path="/system/*" element={<Navigate to="/admin/system/user" replace />} />

        {/* 404 → 对话工作台 */}
        <Route path="*" element={<Navigate to="/chat" replace />} />
      </Routes>
    </BrowserRouter>
  );
};
