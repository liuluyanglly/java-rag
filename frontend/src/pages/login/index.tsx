import React, { useState } from 'react';
import { Form, Input, Button, message } from 'antd';
import { UserOutlined, LockOutlined, ArrowRightOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { useNavigate, Link } from 'react-router-dom';
import { loginApi } from '../../api/auth';
import { useAuthStore } from '../../store/useAuthStore';
import { BrandOrbs, DotMatrixBackground } from '@designcodeio/threeui';

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
    <div className="relative min-h-screen w-full flex items-center justify-center bg-slate-950 px-4 select-none overflow-hidden">
      {/* ======================================================== */}
      {/* MengTo ThreeUI 3D 交互流场背景 (全屏响应式 WebGL)          */}
      {/* ======================================================== */}
      <div className="absolute inset-0 z-0">
        <DotMatrixBackground
          opacity={0.45}
          gridScale={55}
          speed={0.8}
          pulseSpeed={0.5}
          className="w-full h-full"
        />
      </div>

      {/* 渐变光晕背景点缀 */}
      <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-96 h-96 bg-indigo-500/15 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute bottom-10 right-10 w-80 h-80 bg-blue-500/10 rounded-full blur-3xl pointer-events-none" />

      {/* ======================================================== */}
      {/* 硅谷科技感高颜值毛玻璃登录卡片                           */}
      {/* ======================================================== */}
      <div className="relative z-10 w-full max-w-sm bg-white/90 backdrop-blur-2xl rounded-3xl p-8 shadow-2xl border border-white/60 flex flex-col items-center ring-1 ring-slate-900/5">
        {/* MengTo 3D 浮动发光 AI 模型球体 */}
        <div className="relative w-20 h-20 mb-1 flex items-center justify-center cursor-pointer transform hover:scale-105 transition-transform">
          <BrandOrbs variant="gemini" size="medium" speed={1.2} />
        </div>

        {/* 顶部科技感胶囊标签 */}
        <div className="inline-flex items-center gap-1.5 px-3 py-0.5 rounded-full bg-indigo-50 border border-indigo-100/80 text-[11px] font-semibold text-indigo-700 mb-2 shadow-2xs">
          <span className="w-1.5 h-1.5 rounded-full bg-indigo-600 animate-pulse" />
          <span>Spring AI Alibaba 2.0 驱动</span>
        </div>

        {/* 标题 */}
        <h2 className="text-xl font-extrabold text-slate-900 tracking-tight">
          Java RAG Agent 工作台
        </h2>
        <p className="text-xs text-slate-500 mt-1 mb-6 text-center leading-relaxed">
          企业级混合知识库与多 Agent 协同编排引擎
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
            <div className="rounded-xl border border-slate-200/90 bg-white/80 hover:border-indigo-400 focus-within:border-indigo-600 focus-within:ring-2 focus-within:ring-indigo-100 transition-all overflow-hidden px-3.5 py-2.5">
              <Input
                prefix={<UserOutlined className="text-slate-400 mr-2" />}
                placeholder="请输入账号 (如: admin)"
                bordered={false}
                className="text-xs text-slate-900 placeholder:text-slate-400 p-0 font-medium"
              />
            </div>
          </Form.Item>

          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
            className="mb-0"
          >
            <div className="rounded-xl border border-slate-200/90 bg-white/80 hover:border-indigo-400 focus-within:border-indigo-600 focus-within:ring-2 focus-within:ring-indigo-100 transition-all overflow-hidden px-3.5 py-2.5">
              <Input.Password
                prefix={<LockOutlined className="text-slate-400 mr-2" />}
                placeholder="请输入密码 (如: admin123)"
                bordered={false}
                className="text-xs text-slate-900 placeholder:text-slate-400 p-0 font-medium"
              />
            </div>
          </Form.Item>

          <Button
            type="primary"
            htmlType="submit"
            loading={loading}
            block
            className="h-11 rounded-xl bg-gradient-to-r from-indigo-600 via-indigo-700 to-blue-600 hover:from-indigo-500 hover:to-blue-500 border-none font-bold text-xs shadow-md shadow-indigo-500/20 mt-3 flex items-center justify-center gap-1.5"
          >
            <span>立即进入工作台</span>
            <ArrowRightOutlined className="text-xs" />
          </Button>

          <div className="flex items-center justify-between pt-3 text-[11px] text-slate-400 px-1 border-t border-slate-100 mt-2">
            <span className="flex items-center gap-1">
              <SafetyCertificateOutlined className="text-emerald-600" />
              <span>Sa-Token 安全审计</span>
            </span>
            <span className="text-slate-500 font-mono text-[10px]">
              v2.0.0
            </span>
          </div>
        </Form>
      </div>
    </div>
  );
};
