import React, { useState } from 'react';
import { Form, Input, Button, message } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { useNavigate, Link } from 'react-router-dom';
import { loginApi } from '../../api/auth';
import { useAuthStore } from '../../store/useAuthStore';

export const LoginPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { setToken, fetchUserInfo } = useAuthStore();
  const [form] = Form.useForm();

  const handleLogin = async (values: any) => {
    try {
      setLoading(true);
      const res = await loginApi({
        username: values.username,
        password: values.password,
      });
      setToken(res.token);
      message.success('登录成功，欢迎使用智能协同工作台');
      await fetchUserInfo();
      navigate('/chat');
    } catch (e) {
      // 错误拦截器已提示
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen w-full flex items-center justify-center bg-[#f4f5f7] px-4 select-none">
      {/* 扣子风格极简白净登录卡片 */}
      <div className="w-full max-w-sm bg-white rounded-3xl p-8 shadow-sm border border-slate-200/80 flex flex-col items-center">
        {/* 系统 Logo */}
        <div className="w-11 h-11 rounded-2xl bg-slate-900 text-white flex items-center justify-center text-lg font-bold shadow-xs mb-3">
          R
        </div>

        {/* 标题 */}
        <h2 className="text-xl font-bold text-slate-900 tracking-tight">
          登录协同工作台
        </h2>
        <p className="text-xs text-slate-400 mt-1 mb-6">
          Java RAG + Agent 企业智能办公中枢
        </p>

        {/* 登录表单 */}
        <Form
          form={form}
          layout="vertical"
          onFinish={handleLogin}
          initialValues={{ username: 'admin', password: 'admin123' }}
          className="w-full space-y-3.5"
        >
          <Form.Item
            name="username"
            rules={[{ required: true, message: '请输入账号' }]}
            className="mb-0"
          >
            <div className="rounded-xl border border-slate-200 bg-white hover:border-slate-400 focus-within:border-slate-800 transition-colors overflow-hidden px-3 py-2.5">
              <Input
                prefix={<UserOutlined className="text-slate-400 mr-2" />}
                placeholder="请输入账号 (如: admin)"
                bordered={false}
                className="text-xs text-slate-800 placeholder:text-slate-400 p-0"
              />
            </div>
          </Form.Item>

          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
            className="mb-0"
          >
            <div className="rounded-xl border border-slate-200 bg-white hover:border-slate-400 focus-within:border-slate-800 transition-colors overflow-hidden px-3 py-2.5">
              <Input.Password
                prefix={<LockOutlined className="text-slate-400 mr-2" />}
                placeholder="请输入密码 (如: admin123)"
                bordered={false}
                className="text-xs text-slate-800 placeholder:text-slate-400 p-0"
              />
            </div>
          </Form.Item>

          <Button
            type="primary"
            htmlType="submit"
            loading={loading}
            block
            className="h-11 rounded-xl bg-slate-900 hover:!bg-slate-800 border-none font-semibold text-xs shadow-xs mt-2"
          >
            登 录
          </Button>

          <div className="text-center pt-2 text-[11px] text-slate-400">
            管理员入口：
            <Link to="/admin/login" className="text-slate-700 hover:underline font-medium ml-1">
              管理控制台登录 →
            </Link>
          </div>
        </Form>
      </div>
    </div>
  );
};
