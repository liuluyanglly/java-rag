import React, { useState } from 'react';
import { Form, Input, Button, message } from 'antd';
import { UserOutlined, LockOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { useNavigate, Link } from 'react-router-dom';
import { loginApi } from '../../api/auth';
import { useAuthStore } from '../../store/useAuthStore';

export const AdminLoginPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { setToken, fetchUserInfo } = useAuthStore();
  const [form] = Form.useForm();

  const handleLogin = async (values: any) => {
    try {
      setLoading(true);
      const res = await loginApi(values);
      setToken(res.token);
      await fetchUserInfo();

      const { roles } = useAuthStore.getState();
      const isAdmin = roles?.some((r: any) => r.roleKey === 'admin' || r.roleId === 1);
      if (!isAdmin) {
        message.error('权限不足：该账号不是管理员');
        useAuthStore.getState().logout();
        return;
      }

      message.success('管理员验证通过，进入控制台');
      navigate('/admin/dataset');
    } catch (e) {
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen w-full flex items-center justify-center bg-[#f4f5f7] px-4 select-none">
      <div className="w-full max-w-sm bg-white rounded-3xl p-8 shadow-xl border border-slate-200/80 flex flex-col items-center">
        {/* 企业版 Logo */}
        <div className="w-12 h-12 rounded-2xl bg-slate-900 text-white flex items-center justify-center text-xl font-bold shadow-sm mb-3">
          <SafetyCertificateOutlined />
        </div>

        <h2 className="text-xl font-bold text-slate-900 tracking-tight">
          企业管理控制台
        </h2>
        <p className="text-xs text-slate-400 mt-1 mb-6">
          KnowledgeOps Admin · 知识库与权限中心
        </p>

        <Form
          form={form}
          layout="vertical"
          onFinish={handleLogin}
          initialValues={{ username: 'admin', password: 'admin123' }}
          className="w-full space-y-3"
        >
          <Form.Item name="username" rules={[{ required: true, message: '请输入管理员账号' }]}>
            <div className="rounded-xl border border-slate-200 bg-white hover:border-slate-400 focus-within:border-slate-800 transition-colors overflow-hidden px-3 py-2.5">
              <Input
                prefix={<UserOutlined className="text-slate-400 mr-1.5" />}
                placeholder="管理员账号"
                bordered={false}
                className="text-xs text-slate-800 p-0"
              />
            </div>
          </Form.Item>

          <Form.Item name="password" rules={[{ required: true, message: '请输入管理员密码' }]}>
            <div className="rounded-xl border border-slate-200 bg-white hover:border-slate-400 focus-within:border-slate-800 transition-colors overflow-hidden px-3 py-2.5">
              <Input.Password
                prefix={<LockOutlined className="text-slate-400 mr-1.5" />}
                placeholder="管理员密码"
                bordered={false}
                className="text-xs text-slate-800 p-0"
              />
            </div>
          </Form.Item>

          <Button
            type="primary"
            htmlType="submit"
            loading={loading}
            block
            className="h-11 rounded-xl bg-slate-900 hover:!bg-slate-800 border-none font-semibold text-xs mt-2"
          >
            登录管理后台
          </Button>

          <div className="text-center pt-3 text-xs text-slate-400">
            <Link to="/login" className="text-slate-600 hover:text-slate-900 font-medium">
              ← 返回普通用户登录 (个人版)
            </Link>
          </div>
        </Form>
      </div>
    </div>
  );
};
