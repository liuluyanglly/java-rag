import React, { useState, useEffect } from 'react';
import {
  Table,
  Card,
  Input,
  Button,
  Tag,
  Space,
  Drawer,
  Popconfirm,
  message,
  Tooltip,
  Badge,
  Collapse,
} from 'antd';
import {
  MessageOutlined,
  SearchOutlined,
  ReloadOutlined,
  DeleteOutlined,
  EyeOutlined,
  ClearOutlined,
  CopyOutlined,
  UserOutlined,
  RobotOutlined,
  BookOutlined,
  BulbOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  getChatSessionsApi,
  getChatHistoryApi,
  deleteChatSessionApi,
  clearSessionMemoryApi,
  ChatSessionItem,
  ChatMessageItem,
  CitationItem,
} from '../../api/chat';
import { MarkdownRenderer } from '../../components/MarkdownRenderer';

export const ChatHistoryManagePage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [sessions, setSessions] = useState<ChatSessionItem[]>([]);
  const [searchKeyword, setSearchKeyword] = useState('');

  // 抽屉状态
  const [drawerVisible, setDrawerVisible] = useState(false);
  const [selectedSession, setSelectedSession] = useState<ChatSessionItem | null>(null);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [messages, setMessages] = useState<ChatMessageItem[]>([]);

  useEffect(() => {
    loadSessions();
  }, []);

  const loadSessions = async () => {
    setLoading(true);
    try {
      const res = await getChatSessionsApi();
      setSessions(res || []);
    } catch (e) {
      message.error('加载对话会话列表失败');
    } finally {
      setLoading(false);
    }
  };

  // 打开详情抽屉
  const handleViewDetails = async (session: ChatSessionItem) => {
    setSelectedSession(session);
    setDrawerVisible(true);
    setMessagesLoading(true);
    try {
      const historyRes = await getChatHistoryApi(session.sessionId);
      setMessages(historyRes || []);
    } catch (e) {
      message.error('加载会话详细消息失败');
    } finally {
      setMessagesLoading(false);
    }
  };

  // 删除单条会话
  const handleDeleteSession = async (sessionId: string) => {
    try {
      await deleteChatSessionApi(sessionId);
      message.success('会话已彻底删除');
      if (selectedSession?.sessionId === sessionId) {
        setDrawerVisible(false);
      }
      loadSessions();
    } catch (e) {
      message.error('删除会话失败');
    }
  };

  // 清空会话记忆
  const handleClearMemory = async (sessionId: string) => {
    try {
      await clearSessionMemoryApi(sessionId);
      message.success('已清空该会话短期记忆');
      if (selectedSession?.sessionId === sessionId) {
        handleViewDetails(selectedSession);
      }
    } catch (e) {
      message.error('清空记忆失败');
    }
  };

  // 复制文本
  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text);
    message.success('已复制到剪贴板');
  };

  // 过滤后的数据
  const filteredSessions = sessions.filter(
    (s) =>
      s.title?.toLowerCase().includes(searchKeyword.toLowerCase()) ||
      s.sessionId?.toLowerCase().includes(searchKeyword.toLowerCase())
  );

  const columns: ColumnsType<ChatSessionItem> = [
    {
      title: '序号',
      width: 70,
      align: 'center',
      render: (_, __, index) => <span className="font-mono text-xs text-slate-400">{index + 1}</span>,
    },
    {
      title: '会话标题 / 主题',
      dataIndex: 'title',
      key: 'title',
      render: (title: string) => (
        <span className="font-semibold text-slate-800 text-xs">
          {title || '新对话'}
        </span>
      ),
    },
    {
      title: '会话识别码 (Session ID)',
      dataIndex: 'sessionId',
      key: 'sessionId',
      render: (sessionId: string) => (
        <Space size="small">
          <span className="font-mono text-xs text-slate-500 bg-slate-100 px-2 py-0.5 rounded">
            {sessionId}
          </span>
          <Tooltip title="复制会话 ID">
            <Button
              type="text"
              size="small"
              icon={<CopyOutlined className="text-slate-400 hover:text-indigo-600 text-xs" />}
              onClick={() => handleCopy(sessionId)}
            />
          </Tooltip>
        </Space>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 170,
      render: (t: string) => <span className="font-mono text-xs text-slate-500">{t || '-'}</span>,
    },
    {
      title: '最近活跃更新',
      dataIndex: 'updateTime',
      key: 'updateTime',
      width: 170,
      render: (t: string) => <span className="font-mono text-xs text-slate-500">{t || '-'}</span>,
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      align: 'center',
      render: (_, record) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handleViewDetails(record)}
            className="text-indigo-600 hover:text-indigo-500 text-xs"
          >
            查看详情
          </Button>

          <Popconfirm
            title="确定清空此会话的短期上下文记忆吗？"
            onConfirm={() => handleClearMemory(record.sessionId)}
            okText="清空"
            cancelText="取消"
          >
            <Button
              type="text"
              size="small"
              icon={<ClearOutlined />}
              className="text-amber-600 hover:text-amber-500 text-xs"
            >
              清空记忆
            </Button>
          </Popconfirm>

          <Popconfirm
            title="确定删除此会话及其全部聊天记录吗？"
            description="删除后历史问答与检索记录将无法恢复。"
            onConfirm={() => handleDeleteSession(record.sessionId)}
            okText="删除"
            cancelText="取消"
          >
            <Button
              type="text"
              size="small"
              danger
              icon={<DeleteOutlined />}
              className="text-xs"
            >
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="p-6 space-y-5 bg-[#f8fafc] min-h-full">
      {/* 顶部标题与统计看板 */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
            <MessageOutlined className="text-indigo-600" />
            <span>对话记录与审计管理</span>
          </h1>
          <p className="text-xs text-slate-500 mt-1">
            统一监管全平台历史对话会话，支持会话详情深度回溯、推理思考过程审计与短期记忆清退管理。
          </p>
        </div>

        <Space>
          <Button
            icon={<ReloadOutlined />}
            onClick={loadSessions}
            loading={loading}
            className="rounded-xl text-xs font-medium"
          >
            刷新数据
          </Button>
        </Space>
      </div>

      {/* 筛选与检索栏 */}
      <Card className="rounded-2xl shadow-xs border border-slate-200/80 bg-white" styles={{ body: { padding: 16 } }}>
        <div className="flex items-center justify-between gap-4">
          <Input
            placeholder="搜索会话标题、主题关键词或会话 ID..."
            prefix={<SearchOutlined className="text-slate-400" />}
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            allowClear
            className="max-w-md rounded-xl text-xs"
          />
          <div className="text-xs text-slate-400 font-mono">
            共发现 <span className="font-bold text-indigo-600">{filteredSessions.length}</span> 个会话记录
          </div>
        </div>
      </Card>

      {/* 主表格 */}
      <Card className="rounded-2xl shadow-xs border border-slate-200/80 bg-white" styles={{ body: { padding: 0 } }}>
        <Table
          columns={columns}
          dataSource={filteredSessions}
          rowKey="sessionId"
          loading={loading}
          pagination={{
            defaultPageSize: 10,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50'],
            showTotal: (total) => `共 ${total} 条会话记录`,
          }}
          className="overflow-x-auto text-xs"
        />
      </Card>

      {/* 对话回溯审计抽屉 */}
      <Drawer
        title={
          <div className="flex items-center justify-between pr-4">
            <div className="flex items-center gap-2">
              <MessageOutlined className="text-indigo-600" />
              <span className="font-bold text-slate-900">{selectedSession?.title || '会话详情'}</span>
              <Tag color="blue" className="text-[10px] font-mono border-0">
                {messages.length} 条消息
              </Tag>
            </div>
            <span className="font-mono text-xs text-slate-400 font-normal">
              ID: {selectedSession?.sessionId}
            </span>
          </div>
        }
        open={drawerVisible}
        onClose={() => setDrawerVisible(false)}
        width={720}
        styles={{ body: { padding: '20px 24px', backgroundColor: '#f8fafc' } }}
      >
        {messagesLoading ? (
          <div className="h-64 flex items-center justify-center text-xs text-slate-400">
            正在载入对话记录...
          </div>
        ) : messages.length === 0 ? (
          <div className="h-64 flex flex-col items-center justify-center text-xs text-slate-400">
            <MessageOutlined className="text-3xl text-slate-300 mb-2" />
            <span>该会话暂无历史消息记录</span>
          </div>
        ) : (
          <div className="space-y-4">
            {messages.map((msg, idx) => {
              const isUser = msg.role === 'user';
              let citations: CitationItem[] = [];
              try {
                if (msg.citations) {
                  citations = JSON.parse(msg.citations);
                }
              } catch (e) {}

              return (
                <div
                  key={idx}
                  className={`p-4 rounded-2xl border transition-all ${
                    isUser
                      ? 'bg-indigo-50/70 border-indigo-100 text-slate-900'
                      : 'bg-white border-slate-200/90 text-slate-800 shadow-2xs'
                  }`}
                >
                  {/* 消息角色与时间 */}
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2 font-semibold text-xs">
                      {isUser ? (
                        <>
                          <div className="w-5 h-5 rounded-full bg-indigo-600 text-white flex items-center justify-center text-[10px]">
                            <UserOutlined />
                          </div>
                          <span className="text-indigo-700">用户提问 (User Query)</span>
                        </>
                      ) : (
                        <>
                          <div className="w-5 h-5 rounded-full bg-slate-900 text-white flex items-center justify-center text-[10px]">
                            <RobotOutlined />
                          </div>
                          <span className="text-slate-900">AI 智能体回答 (Assistant)</span>
                        </>
                      )}
                    </div>

                    <div className="flex items-center gap-2">
                      <span className="text-[10px] font-mono text-slate-400">
                        {msg.createTime || ''}
                      </span>
                      <Button
                        type="text"
                        size="small"
                        icon={<CopyOutlined className="text-[11px] text-slate-400 hover:text-slate-700" />}
                        onClick={() => handleCopy(msg.content)}
                        title="复制内容"
                      />
                    </div>
                  </div>

                  {/* 思考过程 (Thought) 折叠卡片 */}
                  {!isUser && msg.thought && (
                    <div className="mb-3">
                      <Collapse
                        ghost
                        size="small"
                        items={[
                          {
                            key: 'thought',
                            label: (
                              <span className="text-[11px] font-semibold text-purple-600 flex items-center gap-1">
                                <BulbOutlined />
                                <span>深度推理思考链 (Chain of Thought)</span>
                              </span>
                            ),
                            children: (
                              <div className="p-2.5 rounded-xl bg-purple-50/50 border border-purple-100 text-xs text-slate-600 leading-relaxed font-mono whitespace-pre-wrap">
                                {msg.thought}
                              </div>
                            ),
                          },
                        ]}
                      />
                    </div>
                  )}

                  {/* 正文内容 */}
                  <div className="text-xs leading-relaxed">
                    {isUser ? (
                      <p className="whitespace-pre-wrap">{msg.content}</p>
                    ) : (
                      <MarkdownRenderer content={msg.content} />
                    )}
                  </div>

                  {/* 引用切片证据 (Citations) */}
                  {!isUser && citations.length > 0 && (
                    <div className="mt-3 pt-2.5 border-t border-slate-100">
                      <div className="text-[11px] font-semibold text-slate-500 mb-1.5 flex items-center gap-1">
                        <BookOutlined className="text-emerald-600" />
                        <span>引用的知识库切片证据 ({citations.length} 篇)：</span>
                      </div>
                      <div className="space-y-1">
                        {citations.map((c, cIdx) => (
                          <div
                            key={cIdx}
                            className="p-1.5 rounded-lg bg-slate-50 border border-slate-200/60 text-[11px] flex items-center justify-between"
                          >
                            <span className="text-indigo-600 truncate max-w-sm">
                              📄 {c.documentName || '知识切片'}
                            </span>
                            {c.score !== undefined && (
                              <span className="text-[10px] font-mono text-slate-400">
                                相似度: {Number(c.score).toFixed(3)}
                              </span>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </Drawer>
    </div>
  );
};
