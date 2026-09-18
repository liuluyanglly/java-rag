import React, { useState, useEffect } from 'react';
import { Outlet, useNavigate, useLocation, useSearchParams } from 'react-router-dom';
import {
  PlusOutlined,
  CompassOutlined,
  BookOutlined,
  SettingOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  LogoutOutlined,
  MessageOutlined,
  DeleteOutlined,
} from '@ant-design/icons';
import { Popover, Tooltip, message, Popconfirm } from 'antd';
import { useAuthStore } from '../store/useAuthStore';
import {
  getChatSessionsApi,
  createChatSessionApi,
  deleteChatSessionApi,
  ChatSessionItem,
} from '../api/chat';
import { getActiveAgentsApi, AgentItem } from '../api/agent';

export const UserLayout: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const currentSessionId = searchParams.get('sessionId');

  const { userInfo, roles, logout } = useAuthStore();
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);

  // 业务数据
  const [sessions, setSessions] = useState<ChatSessionItem[]>([]);
  const [agents, setAgents] = useState<AgentItem[]>([]);

  const isAdmin = roles?.some((r: any) => r.roleKey === 'admin' || r.roleId === 1) ?? false;
  const avatarChar = (userInfo?.nickName?.[0] || userInfo?.username?.[0] || '用').toUpperCase();

  useEffect(() => {
    loadSidebarData();
  }, [location.pathname, currentSessionId]);

  useEffect(() => {
    const handleSessionUpdated = (e: any) => {
      const { sessionId, title } = e.detail || {};
      if (sessionId && title) {
        setSessions((prev) =>
          prev.map((s) => (s.sessionId === sessionId ? { ...s, title } : s))
        );
      } else {
        loadSidebarData();
      }
    };
    window.addEventListener('chat-session-updated', handleSessionUpdated);
    return () => {
      window.removeEventListener('chat-session-updated', handleSessionUpdated);
    };
  }, []);

  const loadSidebarData = async () => {
    try {
      const [sessionsRes, agentsRes] = await Promise.all([
        getChatSessionsApi(),
        getActiveAgentsApi(),
      ]);
      setSessions(sessionsRes || []);
      setAgents(agentsRes || []);
    } catch (e) {}
  };

  // 创建新会话 (支持空会话幂等复用，杜绝连续创建无消息的空会话)
  const handleCreateSession = async () => {
    const latestSession = sessions[0];
    if (latestSession && (latestSession.title === '新对话' || !latestSession.title) && currentSessionId === latestSession.sessionId) {
      navigate(`/chat?sessionId=${latestSession.sessionId}`);
      return;
    }
    try {
      const newSession = await createChatSessionApi();
      setSessions((prev) => {
        if (prev.some((s) => s.sessionId === newSession.sessionId)) {
          return prev;
        }
        return [newSession, ...prev];
      });
      navigate(`/chat?sessionId=${newSession.sessionId}`);
      message.success('已开启对话');
    } catch (e) {
      message.error('创建对话失败');
    }
  };

  // 删除指定会话
  const handleDeleteSession = async (e: React.MouseEvent, sessionId: string) => {
    e.stopPropagation();
    try {
      await deleteChatSessionApi(sessionId);
      message.success('对话已删除');
      const updated = sessions.filter((s) => s.sessionId !== sessionId);
      setSessions(updated);

      // 如果删除的是当前会话，自动切换
      if (currentSessionId === sessionId) {
        if (updated.length > 0) {
          navigate(`/chat?sessionId=${updated[0].sessionId}`);
        } else {
          handleCreateSession();
        }
      }
    } catch (e) {
      message.error('删除会话失败');
    }
  };

  // 用户个人菜单
  const userMenuContent = (
    <div className="w-56 p-1 text-slate-800 select-none">
      <div className="flex items-center gap-2.5 p-2 mb-1">
        <div className="w-8 h-8 rounded-full bg-slate-900 text-white flex items-center justify-center font-bold text-xs">
          {avatarChar}
        </div>
        <div className="overflow-hidden">
          <div className="font-bold text-xs text-slate-900 truncate">
            {userInfo?.nickName || userInfo?.username || '当前用户'}
          </div>
          <div className="text-[11px] text-slate-400">
            {isAdmin ? '系统管理员' : '标准用户'}
          </div>
        </div>
      </div>

      <div className="h-[1px] bg-slate-100 my-1" />

      <div className="space-y-0.5 text-xs">
        {isAdmin && (
          <div
            onClick={() => {
              setUserMenuOpen(false);
              navigate('/admin/dataset');
            }}
            className="flex items-center gap-2 p-2 rounded-lg hover:bg-slate-50 cursor-pointer text-indigo-600 font-medium transition-colors"
          >
            <SettingOutlined />
            <span>进入管理控制台</span>
          </div>
        )}

        <div
          onClick={() => {
            setUserMenuOpen(false);
            window.open('http://localhost:8888/scalar', '_blank');
          }}
          className="flex items-center gap-2 p-2 rounded-lg hover:bg-slate-50 cursor-pointer text-slate-600 transition-colors"
        >
          <BookOutlined className="text-slate-400" />
          <span>API 接口文档</span>
        </div>

        <button
          onClick={() => {
            setUserMenuOpen(false);
            logout();
          }}
          className="w-full border-0 bg-transparent flex items-center gap-2 p-2 rounded-lg hover:bg-red-50 text-slate-600 hover:text-red-600 text-xs transition-colors text-left"
        >
          <LogoutOutlined className="text-slate-400" />
          <span>退出登录</span>
        </button>
      </div>
    </div>
  );

  return (
    <div className="fixed inset-0 w-screen h-screen bg-[#f4f5f7] flex p-3 gap-3 overflow-hidden select-none font-sans text-slate-800 box-border">
      {/* ======================================================== */}
      {/* 左侧侧边栏 (自适应弹性高度，消除一切厚重黑框)              */}
      {/* ======================================================== */}
      <aside
        className={`h-full flex flex-col justify-between transition-all duration-200 shrink-0 ${
          sidebarCollapsed ? 'w-14 items-center' : 'w-64'
        } px-2 py-2 box-border`}
      >
        {/* 上半部：固定高度头部 + 自适应可滚动会话列表 */}
        <div className="flex flex-col space-y-3 overflow-hidden flex-1 min-h-0">
          {/* Logo 栏 */}
          <div className="flex items-center justify-between px-1 h-10 shrink-0">
            <div
              className="flex items-center gap-2.5 cursor-pointer"
              onClick={() => navigate('/chat')}
            >
              <div className="w-7 h-7 rounded-xl bg-slate-900 text-white flex items-center justify-center text-xs font-bold shadow-xs shrink-0">
                R
              </div>
              {!sidebarCollapsed && (
                <span className="font-bold text-sm tracking-tight text-slate-900 leading-none">
                  Java RAG Agent
                </span>
              )}
            </div>

            {!sidebarCollapsed ? (
              <button
                onClick={() => setSidebarCollapsed(true)}
                className="border-0 bg-transparent p-1 text-slate-500 hover:text-slate-900 hover:bg-slate-200/60 rounded-md transition-colors"
                title="收起侧边栏"
              >
                <MenuFoldOutlined className="text-xs" />
              </button>
            ) : (
              <button
                onClick={() => setSidebarCollapsed(false)}
                className="border-0 bg-transparent p-1 text-slate-500 hover:text-slate-900 hover:bg-slate-200/60 rounded-md transition-colors"
                title="展开侧边栏"
              >
                <MenuUnfoldOutlined className="text-xs" />
              </button>
            )}
          </div>

          {/* 开启新对话按钮 (纯白微投影，绝无纯黑描边框) */}
          <button
            onClick={handleCreateSession}
            className={`w-full border-0 flex items-center gap-2 px-3 py-2 rounded-xl transition-all ${
              sidebarCollapsed ? 'justify-center' : ''
            } bg-white hover:bg-slate-100/80 text-slate-900 font-semibold text-xs shadow-xs`}
          >
            <PlusOutlined className="text-xs text-slate-700 font-bold" />
            {!sidebarCollapsed && <span>开启新对话</span>}
          </button>

          {/* 功能导航区 (扁平无边框) */}
          <div className="space-y-0.5 text-xs font-semibold text-slate-700 shrink-0">
            <button
              onClick={() => navigate('/chat')}
              className={`w-full border-0 flex items-center gap-2.5 px-2.5 py-1.5 rounded-xl transition-colors text-left ${
                location.pathname === '/chat'
                  ? 'bg-slate-200 text-slate-950 font-bold'
                  : 'bg-transparent hover:bg-slate-200/60 text-slate-700'
              }`}
            >
              <MessageOutlined className="text-sm text-slate-800" />
              {!sidebarCollapsed && <span>智能对话</span>}
            </button>

            <button
              onClick={() => navigate('/research')}
              className={`w-full border-0 flex items-center gap-2.5 px-2.5 py-1.5 rounded-xl transition-colors text-left ${
                location.pathname === '/research'
                  ? 'bg-slate-200 text-slate-950 font-bold'
                  : 'bg-transparent hover:bg-slate-200/60 text-slate-700'
              }`}
            >
              <CompassOutlined className="text-sm text-slate-800" />
              {!sidebarCollapsed && <span>深度研究工坊</span>}
            </button>

            {isAdmin && (
              <button
                onClick={() => navigate('/admin/dataset')}
                className="w-full border-0 bg-transparent flex items-center gap-2.5 px-2.5 py-1.5 rounded-xl hover:bg-slate-200/60 text-slate-700 font-semibold transition-colors text-left"
              >
                <BookOutlined className="text-sm text-slate-800" />
                {!sidebarCollapsed && <span>知识库中枢</span>}
              </button>
            )}
          </div>

          {/* 历史对话列表 (支持对话编号、时间记录、删除对话；弹性自适应 min-h-0) */}
          {!sidebarCollapsed && (
            <div className="pt-2 flex-1 overflow-hidden flex flex-col min-h-0">
              <div className="text-xs font-bold text-slate-600 px-2.5 mb-1.5 tracking-wider uppercase flex items-center justify-between shrink-0">
                <span>历史对话记录</span>
                <span className="text-xs text-slate-500 font-mono font-medium">共 {sessions.length} 条</span>
              </div>

              {/* 滚动容器 */}
              <div className="flex-1 overflow-y-auto space-y-1 pr-1">
                {sessions.length === 0 ? (
                  <div className="px-2.5 py-6 text-center text-slate-500 text-xs font-medium">
                    暂无历史对话
                  </div>
                ) : (
                  sessions.map((s, index) => {
                    const isActive = currentSessionId === s.sessionId;
                    // 对话编号从总数倒序编号或序号显示：如 #1, #2...
                    const sessionNumber = sessions.length - index;

                    return (
                      <div
                        key={s.sessionId}
                        onClick={() => navigate(`/chat?sessionId=${s.sessionId}`)}
                        className={`group relative flex items-center justify-between px-2.5 py-2 rounded-xl cursor-pointer text-xs transition-colors ${
                          isActive
                            ? 'bg-slate-200 text-slate-950 font-bold'
                            : 'text-slate-700 font-medium hover:bg-slate-200/60 hover:text-slate-900'
                        }`}
                      >
                        {/* 编号与标题 */}
                        <div className="flex items-center gap-2 min-w-0 flex-1 mr-1">
                          <span className="text-[11px] font-mono px-1.5 py-0.5 rounded bg-slate-200 text-slate-800 font-bold shrink-0">
                            #{sessionNumber}
                          </span>
                          <Tooltip title={s.title || '新对话'}>
                            <span className="truncate">{s.title || '新对话'}</span>
                          </Tooltip>
                        </div>

                        {/* 时间与删除按钮 */}
                        <div className="flex items-center gap-1.5 shrink-0">
                          <span className="text-[11px] text-slate-500 font-mono group-hover:hidden">
                            {s.createTime ? s.createTime.slice(5, 10) : ''}
                          </span>

                          {/* 悬停展示删除按钮 */}
                          <Popconfirm
                            title="确定删除此对话记录吗？"
                            description="删除后聊天消息与上下文将不可恢复。"
                            onConfirm={(e: any) => handleDeleteSession(e, s.sessionId)}
                            okText="删除"
                            cancelText="取消"
                            placement="right"
                          >
                            <button
                              onClick={(e) => e.stopPropagation()}
                              className="border-0 bg-transparent hidden group-hover:flex items-center justify-center w-5 h-5 rounded hover:bg-red-100 text-slate-500 hover:text-red-600 transition-colors"
                              title="删除会话"
                            >
                              <DeleteOutlined className="text-xs" />
                            </button>
                          </Popconfirm>
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
            </div>
          )}
        </div>

        {/* 底部用户信息卡片 (永久沉底，绝不被溢出截断) */}
        <div className="pt-2 border-t border-slate-200/60 shrink-0">
          <Popover
            content={userMenuContent}
            trigger="click"
            open={userMenuOpen}
            onOpenChange={setUserMenuOpen}
            placement="topRight"
            arrow={false}
          >
            <div className="flex items-center justify-between p-1.5 rounded-xl hover:bg-slate-200/70 cursor-pointer transition-colors">
              <div className="flex items-center gap-2.5 overflow-hidden">
                <div className="w-8 h-8 rounded-full bg-slate-900 text-white flex items-center justify-center font-bold text-xs shrink-0 shadow-xs">
                  {avatarChar}
                </div>
                {!sidebarCollapsed && (
                  <div className="truncate text-left leading-tight">
                    <div className="text-xs font-bold text-slate-900 truncate">
                      {userInfo?.nickName || userInfo?.username || '当前用户'}
                    </div>
                    <div className="text-[11px] text-slate-600 font-medium mt-0.5">
                      {isAdmin ? '系统管理员' : '普通成员'}
                    </div>
                  </div>
                )}
              </div>
              {!sidebarCollapsed && (
                <SettingOutlined className="text-slate-500 hover:text-slate-900 text-xs p-1" />
              )}
            </div>
          </Popover>
        </div>
      </aside>

      {/* ======================================================== */}
      {/* 右侧主画布 (扣子式纯白大圆角卡片)                         */}
      {/* ======================================================== */}
      <main className="flex-1 bg-white rounded-2xl shadow-xs border border-slate-200/60 overflow-hidden flex flex-col relative min-w-0">
        <Outlet />
      </main>
    </div>
  );
};
