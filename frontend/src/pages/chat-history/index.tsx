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
  Spin,
  Empty,
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
  CompressOutlined,
  ExpandOutlined,
  ClockCircleOutlined,
  ApartmentOutlined,
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

  // 展开行状态与每个会话的消息明细缓存
  const [expandedRowKeys, setExpandedRowKeys] = useState<React.Key[]>([]);
  const [sessionMessagesMap, setSessionMessagesMap] = useState<Record<string, ChatMessageItem[]>>({});
  const [sessionLoadingMap, setSessionLoadingMap] = useState<Record<string, boolean>>({});

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

  // 加载某个会话的消息
  const fetchSessionMessages = async (sessionId: string) => {
    if (sessionMessagesMap[sessionId]) {
      return sessionMessagesMap[sessionId];
    }
    setSessionLoadingMap((prev) => ({ ...prev, [sessionId]: true }));
    try {
      const historyRes = await getChatHistoryApi(sessionId);
      const data = historyRes || [];
      setSessionMessagesMap((prev) => ({ ...prev, [sessionId]: data }));
      return data;
    } catch (e) {
      message.error('加载会话详细消息失败');
      return [];
    } finally {
      setSessionLoadingMap((prev) => ({ ...prev, [sessionId]: false }));
    }
  };

  // 点击行展开/折叠
  const handleExpandRow = async (expanded: boolean, record: ChatSessionItem) => {
    if (expanded) {
      setExpandedRowKeys((prev) => [...prev, record.sessionId]);
      if (!sessionMessagesMap[record.sessionId]) {
        await fetchSessionMessages(record.sessionId);
      }
    } else {
      setExpandedRowKeys((prev) => prev.filter((k) => k !== record.sessionId));
    }
  };

  // 一键全部展开 / 全部收起
  const toggleExpandAll = async () => {
    if (expandedRowKeys.length > 0) {
      setExpandedRowKeys([]);
    } else {
      const allKeys = filteredSessions.map((s) => s.sessionId);
      setExpandedRowKeys(allKeys);
      for (const s of filteredSessions) {
        if (!sessionMessagesMap[s.sessionId]) {
          fetchSessionMessages(s.sessionId);
        }
      }
    }
  };

  // 打开详情抽屉
  const handleViewDetails = async (session: ChatSessionItem) => {
    setSelectedSession(session);
    setDrawerVisible(true);
    setMessagesLoading(true);
    try {
      const data = await fetchSessionMessages(session.sessionId);
      setMessages(data);
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
      setExpandedRowKeys((prev) => prev.filter((k) => k !== sessionId));
      setSessions((prev) => prev.filter((s) => s.sessionId !== sessionId));
    } catch (e) {
      message.error('删除会话失败');
    }
  };

  // 清空会话记忆
  const handleClearMemory = async (sessionId: string) => {
    try {
      await clearSessionMemoryApi(sessionId);
      message.success('已清空该会话短期记忆');
      delete sessionMessagesMap[sessionId];
      if (selectedSession?.sessionId === sessionId) {
        handleViewDetails(selectedSession);
      }
      if (expandedRowKeys.includes(sessionId)) {
        fetchSessionMessages(sessionId);
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

  // 过滤后的数据（支持按标题、ID、最新提问、最新回复全匹配检索）
  const filteredSessions = sessions.filter((s) => {
    const kw = searchKeyword.toLowerCase().trim();
    if (!kw) return true;
    return (
      s.title?.toLowerCase().includes(kw) ||
      s.sessionId?.toLowerCase().includes(kw) ||
      s.lastUserMessage?.toLowerCase().includes(kw) ||
      s.lastAssistantMessage?.toLowerCase().includes(kw)
    );
  });

  // 渲染历次对话和回复卡片流
  const renderMessageStream = (msgs: ChatMessageItem[]) => {
    if (!msgs || msgs.length === 0) {
      return (
        <div className="py-6 text-center text-xs text-slate-400 bg-slate-50/50 rounded-xl border border-dashed border-slate-200">
          该会话暂无历史问答消息记录
        </div>
      );
    }

    return (
      <div className="space-y-3.5 my-1">
        {msgs.map((msg, idx) => {
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
              className={`p-3.5 rounded-xl border transition-all text-xs ${
                isUser
                  ? 'bg-indigo-50/70 border-indigo-100/90 text-slate-900'
                  : 'bg-white border-slate-200 text-slate-800 shadow-2xs'
              }`}
            >
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2 font-semibold">
                  {isUser ? (
                    <>
                      <div className="w-5 h-5 rounded-full bg-indigo-600 text-white flex items-center justify-center text-[10px]">
                        <UserOutlined />
                      </div>
                      <span className="text-indigo-700 font-medium">用户提问 (User Query)</span>
                    </>
                  ) : (
                    <>
                      <div className="w-5 h-5 rounded-full bg-slate-900 text-white flex items-center justify-center text-[10px]">
                        <RobotOutlined />
                      </div>
                      <span className="text-slate-900 font-medium">智能体回复 (Assistant Response)</span>
                      {msg.tokens && (
                        <Tag color="cyan" className="text-[10px] font-mono border-0 py-0 px-1.5 ml-1">
                          {msg.tokens} Tokens
                        </Tag>
                      )}
                    </>
                  )}
                </div>

                <div className="flex items-center gap-2 text-[10px] text-slate-400 font-mono">
                  <span>{msg.createTime || ''}</span>
                  <Button
                    type="text"
                    size="small"
                    icon={<CopyOutlined className="text-[11px] text-slate-400 hover:text-indigo-600" />}
                    onClick={() => handleCopy(msg.content)}
                    title="复制内容"
                  />
                </div>
              </div>

              {!isUser && msg.thought && (
                <div className="mb-2.5">
                  <Collapse
                    ghost
                    size="small"
                    items={[
                      {
                        key: 'thought',
                        label: (
                          <span className="text-[11px] font-medium text-purple-600 flex items-center gap-1">
                            <BulbOutlined />
                            <span>查看深度推理思考过程 (Chain of Thought)</span>
                          </span>
                        ),
                        children: (
                          <div className="p-2.5 rounded-lg bg-purple-50/60 border border-purple-100/80 text-[11px] text-slate-600 leading-relaxed font-mono whitespace-pre-wrap">
                            {msg.thought}
                          </div>
                        ),
                      },
                    ]}
                  />
                </div>
              )}

              <div className="leading-relaxed">
                {isUser ? (
                  <p className="whitespace-pre-wrap text-slate-800 text-xs font-normal m-0">{msg.content}</p>
                ) : (
                  <div className="prose prose-xs max-w-none text-xs">
                    <MarkdownRenderer content={msg.content} />
                  </div>
                )}
              </div>

              {!isUser && citations.length > 0 && (
                <div className="mt-2.5 pt-2 border-t border-slate-100">
                  <div className="text-[11px] font-medium text-slate-500 mb-1 flex items-center gap-1">
                    <BookOutlined className="text-emerald-600" />
                    <span>引用知识溯源 ({citations.length} 篇)：</span>
                  </div>
                  <div className="space-y-1">
                    {citations.map((c, cIdx) => (
                      <div
                        key={cIdx}
                        className="p-1.5 rounded-md bg-slate-50 border border-slate-200/60 text-[11px] flex items-center justify-between"
                      >
                        <span className="text-indigo-600 truncate max-w-md">
                          📄 {c.documentName || '知识库文档片段'}
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
    );
  };

  const columns: ColumnsType<ChatSessionItem> = [
    {
      title: '序号',
      width: 60,
      align: 'center',
      render: (_, __, index) => <span className="font-mono text-xs text-slate-400">{index + 1}</span>,
    },
    {
      title: '会话标题 / 轮次',
      dataIndex: 'title',
      key: 'title',
      width: 200,
      render: (title: string, record) => (
        <div className="space-y-1">
          <div className="font-semibold text-slate-900 text-xs truncate max-w-[180px]" title={title || '新对话'}>
            {title || '新对话'}
          </div>
          <div className="flex items-center gap-1">
            <Tag color={record.messageCount ? 'purple' : 'default'} className="text-[10px] font-mono border-0 py-0 px-1.5">
              {record.messageCount !== undefined ? `${record.messageCount} 条记录` : '多轮会话'}
            </Tag>
          </div>
        </div>
      ),
    },
    {
      title: '每次对话和回复内容预览 (最新问答)',
      key: 'latestQA',
      ellipsis: true,
      render: (_, record) => {
        const hasMessages = Boolean(record.lastUserMessage || record.lastAssistantMessage);
        if (!hasMessages) {
          return <span className="text-slate-400 text-xs italic">暂无交互对话</span>;
        }

        return (
          <div className="space-y-1.5 py-1">
            {record.lastUserMessage && (
              <div className="flex items-start gap-1.5">
                <span className="text-[11px] text-indigo-600 bg-indigo-50 border border-indigo-100 rounded px-1.5 py-0.2 font-medium shrink-0 flex items-center gap-1">
                  <UserOutlined className="text-[10px]" /> 问
                </span>
                <span
                  className="text-xs text-slate-700 truncate max-w-xl inline-block"
                  title={record.lastUserMessage}
                >
                  {record.lastUserMessage}
                </span>
              </div>
            )}

            {record.lastAssistantMessage && (
              <div className="flex items-start gap-1.5">
                <span className="text-[11px] text-emerald-700 bg-emerald-50 border border-emerald-100 rounded px-1.5 py-0.2 font-medium shrink-0 flex items-center gap-1">
                  <RobotOutlined className="text-[10px]" /> 答
                </span>
                <span
                  className="text-xs text-slate-600 truncate max-w-xl inline-block"
                  title={record.lastAssistantMessage}
                >
                  {record.lastAssistantMessage}
                </span>
              </div>
            )}
          </div>
        );
      },
    },
    {
      title: '会话识别码',
      dataIndex: 'sessionId',
      key: 'sessionId',
      width: 170,
      render: (sessionId: string) => (
        <Space size="small">
          <span className="font-mono text-xs text-slate-500 bg-slate-100 px-1.5 py-0.5 rounded">
            {sessionId.substring(0, 12)}...
          </span>
          <Tooltip title="复制完整会话 ID">
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
      title: '最后活跃时间',
      dataIndex: 'updateTime',
      key: 'updateTime',
      width: 160,
      render: (t: string) => (
        <span className="font-mono text-[11px] text-slate-500 flex items-center gap-1">
          <ClockCircleOutlined className="text-slate-400 text-[10px]" />
          {t || '-'}
        </span>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      align: 'center',
      render: (_, record) => {
        const isExpanded = expandedRowKeys.includes(record.sessionId);
        return (
          <Space size="small">
            <Button
              type="text"
              size="small"
              icon={isExpanded ? <CompressOutlined /> : <ExpandOutlined />}
              onClick={() => handleExpandRow(!isExpanded, record)}
              className={isExpanded ? 'text-indigo-600 text-xs' : 'text-slate-600 text-xs'}
            >
              {isExpanded ? '收起问答' : '展开问答'}
            </Button>

            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => handleViewDetails(record)}
              className="text-indigo-600 hover:text-indigo-500 text-xs"
            >
              抽屉详情
            </Button>

            <Popconfirm
              title="确定清空此会话记忆吗？"
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
                清空
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
        );
      },
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
            统一监管全平台历史对话会话，支持列表行内直接查阅历次问答和回复、思考链溯源与记忆清退。
          </p>
        </div>

        <Space>
          <Button
            icon={expandedRowKeys.length > 0 ? <CompressOutlined /> : <ExpandOutlined />}
            onClick={toggleExpandAll}
            className="rounded-xl text-xs font-medium text-indigo-600 border-indigo-200 hover:border-indigo-300"
          >
            {expandedRowKeys.length > 0 ? '一键收起全部对话' : '一键展开全部对话'}
          </Button>

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
            placeholder="搜索会话标题、提问内容、AI回复或会话识别码..."
            prefix={<SearchOutlined className="text-slate-400" />}
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            allowClear
            className="max-w-md rounded-xl text-xs"
          />
          <div className="text-xs text-slate-400 font-mono flex items-center gap-3">
            <span>
              共 <span className="font-bold text-indigo-600">{filteredSessions.length}</span> 个会话
            </span>
            {expandedRowKeys.length > 0 && (
              <span className="text-emerald-600">
                已展开 {expandedRowKeys.length} 轮对话流
              </span>
            )}
          </div>
        </div>
      </Card>

      {/* 主表格：支持直接展开历次对话与回复 */}
      <Card className="rounded-2xl shadow-xs border border-slate-200/80 bg-white" styles={{ body: { padding: 0 } }}>
        <Table
          columns={columns}
          dataSource={filteredSessions}
          rowKey="sessionId"
          loading={loading}
          expandable={{
            expandedRowKeys,
            onExpand: handleExpandRow,
            expandRowByClick: false,
            rowExpandable: () => true,
            expandedRowRender: (record) => {
              const isLoading = sessionLoadingMap[record.sessionId];
              const msgs = sessionMessagesMap[record.sessionId];

              return (
                <div className="p-4 bg-slate-50/80 rounded-xl border border-slate-200/80 m-2 space-y-3">
                  <div className="flex items-center justify-between border-b border-slate-200/70 pb-2">
                    <div className="flex items-center gap-2">
                      <ApartmentOutlined className="text-indigo-600" />
                      <span className="font-bold text-slate-800 text-xs">
                        【{record.title || '会话'}】历次对话与回复明细
                      </span>
                      <Tag color="indigo" className="text-[10px] font-mono border-0">
                        {msgs ? `${msgs.length} 条问答消息` : '加载中'}
                      </Tag>
                    </div>
                    <span className="text-[11px] text-slate-400 font-mono">
                      Session ID: {record.sessionId}
                    </span>
                  </div>

                  {isLoading ? (
                    <div className="py-8 text-center text-xs text-slate-400 flex items-center justify-center gap-2">
                      <Spin size="small" />
                      <span>正在拉取该会话历次对话与回复记录...</span>
                    </div>
                  ) : (
                    renderMessageStream(msgs || [])
                  )}
                </div>
              );
            },
          }}
          pagination={{
            defaultPageSize: 10,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50'],
            showTotal: (total) => `共 ${total} 条会话记录`,
          }}
          className="overflow-x-auto text-xs"
        />
      </Card>

      {/* 对话回溯审计抽屉 (侧边全屏深入查看) */}
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
          renderMessageStream(messages)
        )}
      </Drawer>
    </div>
  );
};
