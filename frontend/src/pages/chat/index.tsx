import React, { useState, useEffect, useRef } from 'react';
import {
  Input,
  Button,
  Select,
  Tooltip,
  Empty,
  Spin,
  Modal,
  Popconfirm,
  message,
  Popover,
  Upload,
} from 'antd';
import {
  ArrowUpOutlined,
  PlusOutlined,
  CloudOutlined,
  UserOutlined,
  BarChartOutlined,
  FileSearchOutlined,
  CopyOutlined,
  CheckOutlined,
  LikeOutlined,
  DislikeOutlined,
  ClearOutlined,
  DownOutlined,
  RightOutlined,
  BulbOutlined,
  UploadOutlined,
  DashboardOutlined,
  CompassOutlined,
} from '@ant-design/icons';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { BrandOrbs, DotMatrixBackground } from '@designcodeio/threeui';
import {
  getChatSessionsApi,
  createChatSessionApi,
  getChatHistoryApi,
  streamChat,
  clearSessionMemoryApi,
  submitFeedbackApi,
  ChatSessionItem,
  ChatMessageItem,
  CitationItem,
  AgentMemoryItem,
} from '../../api/chat';
import { getActiveAgentsApi, AgentItem } from '../../api/agent';
import { getAccessibleDatasetsApi, DatasetItem } from '../../api/dataset';
import { TOKEN_HEADER } from '../../api/request';
import { MarkdownRenderer } from '../../components/MarkdownRenderer';
import { useAuthStore } from '../../store/useAuthStore';

const { TextArea } = Input;

// 现代极简双星闪耀 (Sparkles) 高颜值 AI 矢量头像徽章
const AiAvatarBadge: React.FC<{ avatarUrl?: string; name?: string; pulse?: boolean }> = ({
  avatarUrl,
  name,
  pulse = false,
}) => {
  if (avatarUrl && avatarUrl.startsWith('http')) {
    return (
      <img
        src={avatarUrl}
        alt={name || 'AI'}
        className={`w-7 h-7 rounded-xl object-cover shadow-xs border border-slate-200 select-none ${
          pulse ? 'animate-pulse' : ''
        }`}
      />
    );
  }

  return (
    <div
      className={`w-7 h-7 rounded-xl bg-gradient-to-br from-slate-900 via-indigo-950 to-slate-800 text-white flex items-center justify-center shadow-xs select-none border border-slate-700/80 shrink-0 ${
        pulse ? 'animate-pulse ring-2 ring-indigo-400/40' : ''
      }`}
      title={name || 'AI'}
    >
      {/* 现代双星闪耀 (Sparkles) 纯净矢量图标 */}
      <svg
        viewBox="0 0 24 24"
        fill="currentColor"
        className="w-3.5 h-3.5 text-indigo-300"
      >
        <path d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.456 2.456L21.75 6l-1.035.259a3.375 3.375 0 00-2.456 2.456zM16.894 20.567L16.5 21.75l-.394-1.183a2.25 2.25 0 00-1.423-1.423L13.5 18.75l1.183-.394a2.25 2.25 0 001.423-1.423l.394-1.183.394 1.183a2.25 2.25 0 001.423 1.423l1.183.394-1.183.394a2.25 2.25 0 00-1.423 1.423z" />
      </svg>
    </div>
  );
};

// 高级极简纯黑底用户头像徽章
const UserAvatarBadge: React.FC<{ char: string }> = ({ char }) => (
  <div className="w-7 h-7 rounded-full bg-slate-900 text-white flex items-center justify-center font-bold text-xs shadow-xs select-none border border-slate-800 shrink-0">
    {char}
  </div>
);

export const ChatPage: React.FC = () => {
  const navigate = useNavigate();
  const { userInfo, roles } = useAuthStore();
  const isAdmin = roles?.some((r: any) => r.roleKey === 'admin' || r.roleId === 1) ?? false;
  const userName = userInfo?.nickName || userInfo?.username || '当前用户';
  const userAvatarChar = (userInfo?.nickName?.[0] || userInfo?.username?.[0] || '用').toUpperCase();

  const [searchParams, setSearchParams] = useSearchParams();
  const urlSessionId = searchParams.get('sessionId');
  const urlAgentId = searchParams.get('agentId');

  // 会话与状态
  const [currentSessionId, setCurrentSessionId] = useState<string>(urlSessionId || '');
  const [currentSessionTitle, setCurrentSessionTitle] = useState<string>('新对话');
  const [messages, setMessages] = useState<ChatMessageItem[]>([]);
  const [agents, setAgents] = useState<AgentItem[]>([]);
  const [selectedAgentId, setSelectedAgentId] = useState<number | undefined>(
    urlAgentId ? Number(urlAgentId) : undefined
  );
  const [datasets, setDatasets] = useState<DatasetItem[]>([]);
  const [selectedDatasetIds, setSelectedDatasetIds] = useState<number[]>([]);

  // 输入与生成
  const [inputMessage, setInputMessage] = useState('');
  const [generating, setGenerating] = useState(false);
  const [currentThought, setCurrentThought] = useState('');
  const [currentCitations, setCurrentCitations] = useState<CitationItem[]>([]);
  const [currentMemories, setCurrentMemories] = useState<AgentMemoryItem[]>([]);
  const [streamingContent, setStreamingContent] = useState('');
  const [copiedIndex, setCopiedIndex] = useState<number | null>(null);
  const [thoughtExpanded, setThoughtExpanded] = useState<Record<number | string, boolean>>({ live: true });

  // 弹窗状态
  const [uploadModalVisible, setUploadModalVisible] = useState(false);
  const [targetUploadDatasetId, setTargetUploadDatasetId] = useState<number | undefined>();
  const [feedbackModalVisible, setFeedbackModalVisible] = useState(false);
  const [activeFeedbackMsg, setActiveFeedbackMsg] = useState<{ query: string; answer: string } | null>(null);
  const [feedbackComment, setFeedbackComment] = useState('');
  const [activeCitationDetail, setActiveCitationDetail] = useState<CitationItem | null>(null);

  const abortControllerRef = useRef<AbortController | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<any>(null);

  useEffect(() => {
    loadInitialData();
  }, []);

  useEffect(() => {
    if (urlSessionId && urlSessionId !== currentSessionId) {
      switchSession(urlSessionId);
    }
  }, [urlSessionId]);

  useEffect(() => {
    if (urlAgentId) {
      setSelectedAgentId(Number(urlAgentId));
    }
  }, [urlAgentId]);

  const loadInitialData = async () => {
    try {
      const [sessionsRes, agentsRes, datasetsRes] = await Promise.all([
        getChatSessionsApi(),
        getActiveAgentsApi(),
        getAccessibleDatasetsApi(),
      ]);
      setAgents(agentsRes || []);
      setDatasets(datasetsRes || []);

      if (!selectedAgentId && agentsRes.length > 0) {
        setSelectedAgentId(agentsRes[0].id);
      }
      if (datasetsRes.length > 0) {
        setSelectedDatasetIds(datasetsRes.map((d) => d.id));
        setTargetUploadDatasetId(datasetsRes[0].id);
      }

      if (urlSessionId) {
        switchSession(urlSessionId);
      } else if (sessionsRes && sessionsRes.length > 0) {
        switchSession(sessionsRes[0].sessionId, sessionsRes[0].title);
      } else {
        handleCreateSession();
      }
    } catch (e) {}
  };

  const switchSession = async (sessionId: string, title?: string) => {
    setCurrentSessionId(sessionId);
    if (title) setCurrentSessionTitle(title);
    setSearchParams({ sessionId });
    setMessages([]);
    setCurrentThought('');
    setCurrentCitations([]);
    setCurrentMemories([]);
    setStreamingContent('');
    try {
      const history = await getChatHistoryApi(sessionId);
      setMessages(history || []);
      scrollToBottom();
    } catch (e) {}
  };

  const handleCreateSession = async () => {
    // 若当前已经是空的会话（尚未发送任何消息），直接聚焦输入框，杜绝重复创建
    if (messages.length === 0 && currentSessionId) {
      inputRef.current?.focus();
      return;
    }
    try {
      const newSession = await createChatSessionApi(selectedAgentId);
      if (newSession.sessionId === currentSessionId) {
        inputRef.current?.focus();
        return;
      }
      setCurrentSessionId(newSession.sessionId);
      setCurrentSessionTitle(newSession.title || '新对话');
      setSearchParams({ sessionId: newSession.sessionId });
      setMessages([]);
      setCurrentThought('');
      setCurrentCitations([]);
      setCurrentMemories([]);
      setStreamingContent('');
      inputRef.current?.focus();
    } catch (e) {
      message.error('创建会话失败');
    }
  };

  const scrollToBottom = () => {
    setTimeout(() => {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, 100);
  };

  const handleStopGenerating = () => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }
    setGenerating(false);
    message.info('已停止生成');
  };

  const handleClearSession = async () => {
    try {
      await clearSessionMemoryApi(currentSessionId);
      setMessages([]);
      message.success('已清空会话内容');
    } catch (e) {
      message.error('清空失败');
    }
  };

  const handleCopyMessage = (text: string, index: number) => {
    navigator.clipboard.writeText(text);
    setCopiedIndex(index);
    message.success('已复制');
    setTimeout(() => setCopiedIndex(null), 2000);
  };

  const handleSend = (textOverride?: string) => {
    const textToSend = (textOverride || inputMessage).trim();
    if (!textToSend || generating) return;

    setInputMessage('');

    const userMsg: ChatMessageItem = {
      sessionId: currentSessionId,
      role: 'user',
      content: textToSend,
      createTime: new Date().toLocaleTimeString(),
    };
    setMessages((prev) => [...prev, userMsg]);
    setGenerating(true);
    setCurrentThought('');
    setCurrentCitations([]);
    setCurrentMemories([]);
    setStreamingContent('');
    setThoughtExpanded((prev) => ({ ...prev, live: true }));
    scrollToBottom();

    let fullRawText = '';
    let fullText = '';
    let thoughtText = '';
    let citationsArr: CitationItem[] = [];
    let memoriesArr: AgentMemoryItem[] = [];

    const controller = streamChat(
      {
        sessionId: currentSessionId,
        agentId: selectedAgentId,
        message: textToSend,
        datasetIds: selectedDatasetIds,
      },
      {
        onThought: (thought) => {
          thoughtText += thought;
          setCurrentThought(thoughtText);
          scrollToBottom();
        },
        onSessionTitle: (title, sId) => {
          if (title) {
            setCurrentSessionTitle(title);
            window.dispatchEvent(
              new CustomEvent('chat-session-updated', {
                detail: { sessionId: sId || currentSessionId, title },
              })
            );
          }
        },
        onCitations: (citations) => {
          citationsArr = citations;
          setCurrentCitations(citations);
        },
        onMemories: (memories) => {
          memoriesArr = memories;
          setCurrentMemories(memories);
        },
        onMessage: (token) => {
          fullRawText += token;
          // 支持自动识别与实时抽离模型原生的 <think>...</think> 思考链
          if (fullRawText.includes('<think>')) {
            const thinkEndIdx = fullRawText.indexOf('</think>');
            if (thinkEndIdx !== -1) {
              const modelThought = fullRawText.slice(fullRawText.indexOf('<think>') + 7, thinkEndIdx).trim();
              if (modelThought && !thoughtText.includes(modelThought)) {
                thoughtText += (thoughtText.endsWith('\n') ? '' : '\n') + modelThought + '\n';
                setCurrentThought(thoughtText);
              }
              fullText = fullRawText.slice(thinkEndIdx + 8).trimStart();
            } else {
              // 还在思考中
              const liveThink = fullRawText.slice(fullRawText.indexOf('<think>') + 7);
              setCurrentThought(thoughtText + '\n' + liveThink);
              fullText = '';
            }
          } else {
            fullText = fullRawText;
          }

          setStreamingContent(fullText);
          scrollToBottom();
        },
        onFinish: (finalMsg) => {
          if (finalMsg?.sessionTitle) {
            setCurrentSessionTitle(finalMsg.sessionTitle);
            window.dispatchEvent(
              new CustomEvent('chat-session-updated', {
                detail: { sessionId: finalMsg.sessionId || currentSessionId, title: finalMsg.sessionTitle },
              })
            );
          }
          setMessages((prev) => [
            ...prev,
            {
              ...finalMsg,
              content: fullText || fullRawText,
              thought: finalMsg?.thought || thoughtText,
              citations: citationsArr.length > 0 ? JSON.stringify(citationsArr) : (finalMsg?.citations || '[]'),
              memories: memoriesArr.length > 0 ? JSON.stringify(memoriesArr) : (finalMsg?.memories || '[]'),
            },
          ]);
          setGenerating(false);
          setStreamingContent('');
          setCurrentThought('');
          setCurrentCitations([]);
          setCurrentMemories([]);
        },
        onError: (err) => {
          setGenerating(false);
          message.error('生成遇到错误: ' + err);
        },
      }
    );

    abortControllerRef.current = controller;
  };

  const handleSubmitFeedback = async () => {
    if (!activeFeedbackMsg) return;
    try {
      await submitFeedbackApi({
        sessionId: currentSessionId,
        agentId: selectedAgentId,
        query: activeFeedbackMsg.query,
        answer: activeFeedbackMsg.answer,
        rating: -1,
        comment: feedbackComment,
      });
      setFeedbackModalVisible(false);
      setFeedbackComment('');
      message.success('已记录反馈');
    } catch (e) {
      message.error('提交反馈异常');
    }
  };

  const currentAgent = agents.find((a) => a.id === selectedAgentId);

  // 根据当前 Agent 动态匹配 ThreeUI 3D 品牌球 Variant
  const getAgentOrbVariant = (name?: string): 'gemini' | 'openai' | 'claude' | 'codex' => {
    if (!name) return 'gemini';
    if (name.includes('代码') || name.includes('架构') || name.includes('技术')) return 'codex';
    if (name.includes('分析') || name.includes('SQL') || name.includes('数据')) return 'openai';
    if (name.includes('研究') || name.includes('学术') || name.includes('调研')) return 'claude';
    return 'gemini';
  };

  // 推荐引导卡片 (高频企业级 RAG 与 Agent 实战场景)
  const quickCards = [
    {
      icon: <FileSearchOutlined className="text-indigo-500 text-base" />,
      title: '知识库混合召回',
      desc: '多路向量+全文重排序检索技术规范',
      prompt: '请帮我检索知识库中关于系统架构设计与核心技术栈的详细规范要求。',
    },
    {
      icon: <BarChartOutlined className="text-emerald-500 text-base" />,
      title: '响应式架构对比',
      desc: 'WebFlux 与虚拟线程对比分析',
      prompt: '请针对响应式架构与传统阻塞式架构进行全方位对比分析，并输出 Markdown 结构化表格。',
    },
    {
      icon: <CompassOutlined className="text-amber-500 text-base" />,
      title: '多 Agent 协同调研',
      desc: '状态机自动化编排生成深度研报',
      prompt: '请规划一个多 Agent 协同方案，涵盖规划、信息检索、批判反思与终稿合成全流程。',
    },
    {
      icon: <UserOutlined className="text-purple-500 text-base" />,
      title: '双轨记忆与反省',
      desc: '持久化开发习惯与项目上下文',
      prompt: '请记住我的开发偏好：项目严格基于 Spring Boot 4.0.0 与 Netty WebFlux 响应式架构！',
    },
  ];

  return (
    <div className="h-full w-full flex flex-col bg-white text-slate-800 overflow-hidden relative box-border">
      {/* 顶部极简信息条 (充足安全高度，确保完全无遮挡与截断) */}
      <div className="h-14 px-6 border-b border-slate-100 flex items-center justify-between shrink-0 bg-white z-20 select-none box-border">
        <div className="flex items-center gap-2.5 min-w-0">
          <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 shrink-0 shadow-xs" />
          <span className="font-semibold text-sm text-slate-900 truncate leading-none">
            {currentAgent?.name || '智能助手'}
          </span>
          <span className="text-xs text-slate-400 font-mono hidden sm:inline truncate leading-none">
            · {currentSessionTitle}
          </span>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          {messages.length > 0 && (
            <Popconfirm
              title="确定清空当前会话内容吗？"
              onConfirm={handleClearSession}
              okText="清空"
              cancelText="取消"
            >
              <button className="border-0 bg-transparent flex items-center gap-1.5 text-xs text-slate-400 hover:text-red-500 px-2.5 py-1.5 rounded-lg hover:bg-red-50/60 transition-all cursor-pointer">
                <ClearOutlined />
                <span>清空记录</span>
              </button>
            </Popconfirm>
          )}
        </div>
      </div>

      {/* 首屏 3D 点阵背景 (仅在消息为空时以轻量透明度呈现，pointer-events-none 不影响交互) */}
      {messages.length === 0 && !generating && (
        <div className="absolute inset-0 pointer-events-none z-0 overflow-hidden opacity-20">
          <DotMatrixBackground opacity={0.35} gridScale={58} speed={0.6} pulseSpeed={0.4} />
        </div>
      )}

      {/* 中间消息画卷 / 首屏欢迎区 (留足 pb-36 底部内边距，保证与吸底输入框完美重叠而不遮挡内容) */}
      <div className={`flex-1 overflow-y-auto overflow-x-hidden px-4 sm:px-8 md:px-16 pt-6 space-y-6 min-h-0 relative z-10 ${messages.length > 0 ? 'pb-40' : 'pb-6'}`}>
        {/* 首屏未提问状态 (ThreeUI 3D 球体 + 极简高质感卡片) */}
        {messages.length === 0 && !generating && (
          <div className="h-full flex flex-col items-center justify-center -mt-3 max-w-3xl w-full mx-auto">
            {/* 3D 浮空动态交互核心球 (ThreeUI 原生 WebGL 驱动) */}
            <div className="relative mb-3 flex flex-col items-center justify-center select-none">
              <div className="w-20 h-20 flex items-center justify-center transition-transform duration-500 hover:scale-110 cursor-pointer drop-shadow-[0_8px_24px_rgba(99,102,241,0.25)]">
                <BrandOrbs variant={getAgentOrbVariant(currentAgent?.name)} size="medium" speed={1.1} />
              </div>
              <div className="mt-1 text-[10px] font-bold text-slate-400 tracking-wider uppercase">
                {currentAgent?.name || 'Java-RAG Enterprise Agent'}
              </div>
            </div>

            {/* 居中大字标题 */}
            <h1 className="text-2xl md:text-3xl font-extrabold text-slate-900 mb-6 tracking-tight flex items-center gap-2 text-center">
              <span>今天想和</span>
              <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-slate-100/90 backdrop-blur-md text-slate-900 text-lg md:text-xl font-bold border border-slate-200/80 shadow-xs">
                <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 shadow-xs" />
                <span>{currentAgent?.name || '智能助手'}</span>
              </span>
              <span>探讨什么？</span>
            </h1>

            {/* 核心输入卡片 (边框加深对比明显、轻微立体阴影、聚焦状态蓝色光晕反馈) */}
            <div className="w-full rounded-2xl border border-slate-400/80 bg-white/95 backdrop-blur-xl p-3.5 shadow-sm hover:border-slate-500 focus-within:border-indigo-500 focus-within:ring-3 focus-within:ring-indigo-100/80 transition-all">
              <TextArea
                ref={inputRef}
                value={inputMessage}
                onChange={(e) => setInputMessage(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    handleSend();
                  }
                }}
                placeholder={`向 ${currentAgent?.name || '智能助手'} 提问，或输入业务需求...`}
                autoSize={{ minRows: 3, maxRows: 8 }}
                bordered={false}
                className="resize-none text-sm text-slate-900 placeholder:text-slate-500 p-1 font-medium"
              />

              {/* 输入框底部动作条 (下拉框自适应，无生硬黑框) */}
              <div className="flex flex-wrap items-center justify-between pt-2 border-t border-slate-100 mt-1 gap-2">
                {/* 左侧：+ 号按钮、知识库挂载、Agent 切换 (自适应宽度) */}
                <div className="flex items-center gap-2 flex-wrap min-w-0">
                  <Tooltip title="上传本地文档至知识库">
                    <button
                      onClick={() => setUploadModalVisible(true)}
                      className="border-0 w-7 h-7 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 flex items-center justify-center text-xs transition-colors shrink-0"
                    >
                      <PlusOutlined className="font-bold" />
                    </button>
                  </Tooltip>

                  {/* 知识库挂载 Popover */}
                  <Popover
                    placement="bottomLeft"
                    trigger="click"
                    content={
                      <div className="w-72 p-1 text-xs space-y-2">
                        <div className="font-bold text-slate-800">挂载检索知识库</div>
                        <Select
                          mode="multiple"
                          className="w-full text-xs"
                          placeholder="选择知识库"
                          value={selectedDatasetIds}
                          onChange={setSelectedDatasetIds}
                          options={datasets.map((d) => ({
                            label: `${d.name} (${d.docCount || 0}篇)`,
                            value: d.id,
                          }))}
                        />
                      </div>
                    }
                  >
                    <button className="border-0 flex items-center gap-1.5 text-xs text-slate-700 hover:text-slate-950 font-semibold px-2.5 py-1 rounded-lg bg-slate-100 hover:bg-slate-200 transition-colors shrink-0">
                      <CloudOutlined className="text-xs text-slate-600" />
                      <span>知识库 ({selectedDatasetIds.length})</span>
                    </button>
                  </Popover>

                  {/* 智能体切换下拉框 (自适应弹性宽度) */}
                  <Select
                    value={selectedAgentId}
                    onChange={setSelectedAgentId}
                    bordered={false}
                    popupMatchSelectWidth={false}
                    className="text-xs font-bold text-slate-800 hover:bg-slate-100 rounded-lg max-w-[180px] sm:max-w-[220px]"
                    options={agents.map((a) => ({
                      label: (
                        <span className="flex items-center gap-1.5 truncate">
                          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 shrink-0" />
                          <span className="truncate">{a.name}</span>
                        </span>
                      ),
                      value: a.id,
                    }))}
                  />
                </div>

                {/* 右侧：上箭头发送按钮 (纯色圆角无黑描边) */}
                <div className="flex items-center gap-2 shrink-0">
                  <button
                    onClick={() => handleSend()}
                    disabled={!inputMessage.trim()}
                    className={`border-0 w-8 h-8 rounded-full flex items-center justify-center text-white text-xs transition-all ${
                      inputMessage.trim()
                        ? 'bg-slate-900 hover:bg-slate-800 cursor-pointer shadow-xs'
                        : 'bg-slate-200 text-slate-400 cursor-not-allowed'
                    }`}
                  >
                    <ArrowUpOutlined className="font-bold" />
                  </button>
                </div>
              </div>
            </div>

            {/* 输入框下方的 4 个实战场景推荐卡片 */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2.5 w-full mt-4">
              {quickCards.map((card, idx) => (
                <div
                  key={idx}
                  onClick={() => handleSend(card.prompt)}
                  className="p-3 rounded-xl border border-slate-200/90 bg-white/80 backdrop-blur-md hover:bg-white hover:border-indigo-300 hover:shadow-md cursor-pointer transition-all flex flex-col justify-between group text-left"
                >
                  <div className="flex items-center justify-between mb-2">
                    <span className="p-1.5 rounded-lg bg-slate-50 group-hover:bg-indigo-50 transition-colors">
                      {card.icon}
                    </span>
                    <span className="text-[10px] text-slate-400 font-mono font-bold">0{idx + 1}</span>
                  </div>
                  <div>
                    <div className="text-xs font-bold text-slate-800 group-hover:text-indigo-600 transition-colors line-clamp-1">
                      {card.title}
                    </div>
                    <div className="text-[11px] text-slate-400 line-clamp-1 mt-0.5">
                      {card.desc}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* 对话消息流 (头像与名字严格同行对齐、扣子高颜值AI徽章、高对比度清晰气泡) */}
        {messages.map((m, idx) => {
          const isUser = m.role === 'user';
          const citations: CitationItem[] = m.citations ? JSON.parse(m.citations) : [];
          const isThoughtOpen = thoughtExpanded[idx] ?? false;

          return (
            <div key={idx} className="max-w-3xl w-full mx-auto space-y-1.5">
              {/* 顶部标题栏：头像与名字、身份标签、时间戳严格在同一行水平居中对齐 */}
              <div
                className={`flex items-center gap-2 select-none ${
                  isUser ? 'flex-row-reverse' : 'flex-row'
                }`}
              >
                {/* 独立精美头像 */}
                {isUser ? (
                  <UserAvatarBadge char={userAvatarChar} />
                ) : (
                  <AiAvatarBadge
                    avatarUrl={currentAgent?.avatar}
                    name={currentAgent?.name}
                  />
                )}

                {/* 名字 (与头像中心绝对对齐) */}
                <span className="font-bold text-xs text-slate-900 leading-none">
                  {isUser ? userName : (currentAgent?.name || '智能助手')}
                </span>

                {/* 生成时间 */}
                {m.createTime && (
                  <span className="text-[11px] text-slate-400 font-normal font-mono leading-none">
                    {m.createTime}
                  </span>
                )}
              </div>

              {/* 下方正文与卡片 (留出缩进使气泡与文字垂直严密对齐) */}
              <div className={`flex ${isUser ? 'justify-end pr-9' : 'justify-start pl-9'}`}>
                <div
                  className={`space-y-1.5 max-w-[92%] flex flex-col ${
                    isUser ? 'items-end' : 'items-start'
                  }`}
                >
                  {/* 思考过程与检索轨迹卡片 (仅 AI) */}
                  {!isUser && m.thought && (
                    <div className="w-full rounded-xl border border-slate-200/90 bg-slate-50 text-xs overflow-hidden shadow-2xs">
                      <div
                        onClick={() =>
                          setThoughtExpanded((prev) => ({ ...prev, [idx]: !isThoughtOpen }))
                        }
                        className="px-3 py-1.5 flex items-center justify-between cursor-pointer text-slate-700 hover:text-slate-950 select-none font-semibold transition-colors"
                      >
                        <span className="flex items-center gap-1.5">
                          <BulbOutlined className="text-indigo-500" />
                          <span>已完成多智能体协同思考与四维检索轨迹</span>
                        </span>
                        <div className="flex items-center gap-1 text-[11px] text-slate-400 font-normal">
                          <span>{isThoughtOpen ? '收起' : '展开'}</span>
                          {isThoughtOpen ? <DownOutlined className="text-[9px]" /> : <RightOutlined className="text-[9px]" />}
                        </div>
                      </div>
                      {isThoughtOpen && (
                        <div className="px-3 py-2.5 border-t border-slate-200 text-[11px] font-mono whitespace-pre-wrap text-slate-700 bg-white/80 leading-relaxed">
                          {m.thought}
                        </div>
                      )}
                    </div>
                  )}

                  {/* 消息正文气泡 */}
                  <div
                    className={`text-sm leading-relaxed p-3.5 shadow-xs border ${
                      isUser
                        ? 'bg-slate-900 text-white border-slate-900 rounded-2xl rounded-tr-xs font-normal'
                        : 'bg-white border-slate-200/90 text-slate-900 rounded-2xl rounded-tl-xs'
                    }`}
                  >
                    {isUser ? (
                      <div className="whitespace-pre-wrap font-normal">{m.content}</div>
                    ) : (
                      <MarkdownRenderer content={m.content} />
                    )}

                    {/* 知识库溯源引用 */}
                    {citations.length > 0 && (
                      <div className="mt-2.5 pt-2 border-t border-slate-100 flex flex-wrap gap-1.5">
                        {citations.map((c, cIdx) => (
                          <button
                            key={cIdx}
                            onClick={() => setActiveCitationDetail(c)}
                            className="border-0 flex items-center gap-1 px-2 py-0.5 rounded-md bg-slate-100 hover:bg-slate-200 text-[11px] text-slate-700 font-medium transition-colors"
                          >
                            📄 <span>{c.documentName}</span>
                          </button>
                        ))}
                      </div>
                    )}

                    {/* 消息操作条 (纯图标，消除黑框) */}
                    {!isUser && (
                      <div className="mt-2 pt-1.5 flex items-center gap-2.5 text-slate-500 text-xs">
                        <button
                          onClick={() => handleCopyMessage(m.content, idx)}
                          className="border-0 bg-transparent hover:text-slate-900 p-1 rounded-md transition-colors"
                          title="复制回答"
                        >
                          {copiedIndex === idx ? <CheckOutlined className="text-emerald-500" /> : <CopyOutlined />}
                        </button>
                        <button
                          onClick={() => message.success('已记录有效偏好')}
                          className="border-0 bg-transparent hover:text-slate-900 p-1 rounded-md transition-colors"
                          title="回答准确"
                        >
                          <LikeOutlined />
                        </button>
                        <button
                          onClick={() => {
                            setActiveFeedbackMsg({ query: '', answer: m.content });
                            setFeedbackModalVisible(true);
                          }}
                          className="border-0 bg-transparent hover:text-slate-900 p-1 rounded-md transition-colors"
                          title="回答有误？指正并优化"
                        >
                          <DislikeOutlined />
                        </button>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            </div>
          );
        })}

        {/* 正在生成中 */}
        {generating && (
          <div className="max-w-3xl w-full mx-auto space-y-1.5">
            <div className="flex items-center gap-2 select-none">
              <AiAvatarBadge
                avatarUrl={currentAgent?.avatar}
                name={currentAgent?.name}
                pulse={true}
              />
              <span className="font-bold text-xs text-slate-900 leading-none">
                {currentAgent?.name || '智能助手'}
              </span>
              <span className="px-1.5 py-0.5 rounded bg-emerald-50 border border-emerald-200 text-emerald-700 text-[10px] font-semibold animate-pulse leading-none">
                思考与组织回答中...
              </span>
            </div>

            <div className="flex justify-start pl-9">
              <div className="space-y-1.5 max-w-[92%] w-full flex flex-col items-start">
                {currentThought && (
                  <div className="w-full rounded-xl border border-indigo-100 bg-gradient-to-br from-indigo-50/60 via-slate-50 to-white text-xs p-3 font-mono text-slate-700 shadow-2xs mb-1">
                    <div className="flex items-center justify-between font-semibold mb-1.5 text-indigo-950">
                      <span className="flex items-center gap-1.5">
                        <Spin size="small" /> <span>🧠 多智能体协同思考与四维检索推导</span>
                      </span>
                      <span className="text-[10px] text-indigo-600 font-normal">多智能体实时协作中...</span>
                    </div>
                    <div className="whitespace-pre-wrap text-[11px] leading-relaxed text-slate-700 bg-white/90 p-2.5 rounded-lg border border-indigo-100/60 max-h-72 overflow-y-auto">
                      {currentThought}
                    </div>
                  </div>
                )}
                {streamingContent ? (
                  <div className="text-sm leading-relaxed p-3.5 bg-white border border-slate-200/90 text-slate-900 rounded-2xl rounded-tl-xs shadow-xs w-full">
                    <MarkdownRenderer content={streamingContent} />
                    <span className="inline-block w-1.5 h-4 ml-1 bg-indigo-500 animate-pulse align-middle rounded-xs" />
                  </div>
                ) : (
                  <div className="flex items-center gap-2 text-xs text-slate-600 font-medium py-2">
                    <Spin size="small" /> 智能体正在生成回答...
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* 对话进行中的底部输入框：浮动悬浮吸底卡片，与对话区无缝融合重叠 */}
      {messages.length > 0 && (
        <div className="absolute bottom-0 left-0 right-0 px-4 sm:px-8 md:px-16 pb-5 pt-8 pointer-events-none bg-gradient-to-t from-white via-white/90 to-transparent z-10 flex justify-center">
          <div className="max-w-3xl w-full pointer-events-auto rounded-2xl border border-slate-300 bg-white/95 backdrop-blur-md p-3 shadow-lg hover:border-slate-400 focus-within:border-indigo-500 focus-within:ring-2 focus-within:ring-indigo-100 transition-all">
            <TextArea
              ref={inputRef}
              value={inputMessage}
              onChange={(e) => setInputMessage(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  handleSend();
                }
              }}
              placeholder="发送消息或提出问题..."
              autoSize={{ minRows: 1, maxRows: 6 }}
              bordered={false}
              className="resize-none text-sm text-slate-900 placeholder:text-slate-500 p-1 font-medium"
            />

            <div className="flex flex-wrap items-center justify-between pt-2 border-t border-slate-100 mt-1 gap-2">
              <div className="flex items-center gap-2 flex-wrap">
                <button
                  onClick={() => setUploadModalVisible(true)}
                  className="border-0 w-7 h-7 rounded-lg bg-slate-100 hover:bg-slate-200 flex items-center justify-center text-slate-700 text-xs transition-colors shrink-0"
                  title="上传文档"
                >
                  <PlusOutlined className="font-bold" />
                </button>
                <button
                  onClick={() => setUploadModalVisible(true)}
                  className="border-0 flex items-center gap-1.5 text-xs text-slate-700 hover:text-slate-950 font-semibold px-2 py-0.5 rounded-lg bg-slate-100 hover:bg-slate-200 transition-colors shrink-0"
                >
                  <CloudOutlined className="text-xs text-slate-600" />
                  <span>知识库</span>
                </button>
              </div>

              <div className="flex items-center gap-2 shrink-0">
                {generating ? (
                  <button
                    onClick={handleStopGenerating}
                    className="border-0 w-7 h-7 rounded-full bg-slate-800 hover:bg-black text-white flex items-center justify-center text-xs"
                    title="停止生成"
                  >
                    ■
                  </button>
                ) : (
                  <button
                    onClick={() => handleSend()}
                    disabled={!inputMessage.trim()}
                    className={`border-0 w-8 h-8 rounded-full flex items-center justify-center text-white text-xs transition-all ${
                      inputMessage.trim()
                        ? 'bg-slate-900 hover:bg-slate-800 cursor-pointer shadow-xs'
                        : 'bg-slate-200 text-slate-400 cursor-not-allowed'
                    }`}
                  >
                    <ArrowUpOutlined />
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 上传文档到知识库弹窗 */}
      <Modal
        title="上传本地文档至知识库"
        open={uploadModalVisible}
        onCancel={() => setUploadModalVisible(false)}
        footer={null}
        width={440}
      >
        <div className="space-y-4 py-2">
          <div>
            <label className="block text-xs font-semibold text-slate-600 mb-1.5">
              目标知识库：
            </label>
            <Select
              className="w-full text-xs"
              value={targetUploadDatasetId}
              onChange={setTargetUploadDatasetId}
              options={datasets.map((d) => ({
                label: d.name,
                value: d.id,
              }))}
            />
          </div>

          <Upload.Dragger
            action={`/api/document/upload?datasetId=${targetUploadDatasetId}`}
            headers={{ [TOKEN_HEADER]: localStorage.getItem('token') ?? '' }}
            multiple={false}
            showUploadList={true}
            onChange={(info) => {
              if (info.file.status === 'done') {
                message.success(`《${info.file.name}》上传成功！后台正在自动切片。`);
                setUploadModalVisible(false);
                getAccessibleDatasetsApi().then(setDatasets);
              } else if (info.file.status === 'error') {
                message.error(`${info.file.name} 上传失败`);
              }
            }}
            className="rounded-2xl"
          >
            <p className="text-slate-400 mb-1 text-2xl">
              <UploadOutlined />
            </p>
            <p className="text-xs font-semibold text-slate-700">
              点击或拖拽文件到此处上传
            </p>
            <p className="text-[11px] text-slate-400 mt-1">
              支持 PDF, Word (.docx), Markdown (.md), TXT 等格式
            </p>
          </Upload.Dragger>
        </div>
      </Modal>

      {/* 知识切片来源查看 */}
      <Modal
        title="📄 知识切片原文"
        open={!!activeCitationDetail}
        onCancel={() => setActiveCitationDetail(null)}
        footer={null}
        width={560}
      >
        <div className="space-y-2 py-1 text-xs">
          <div className="font-semibold text-slate-700">
            {activeCitationDetail?.documentName}
          </div>
          <div className="p-3 rounded-xl bg-slate-50 text-slate-700 leading-relaxed font-mono whitespace-pre-wrap text-[11px]">
            {activeCitationDetail?.content}
          </div>
        </div>
      </Modal>

      {/* 指正反馈弹窗 */}
      <Modal
        title="回答指正与反馈"
        open={feedbackModalVisible}
        onOk={handleSubmitFeedback}
        okText="提交反馈"
        cancelText="取消"
        onCancel={() => setFeedbackModalVisible(false)}
      >
        <div className="py-2 text-xs space-y-2">
          <p className="text-slate-500">指出回答中的不准确之处，智能体将反思并沉淀规约：</p>
          <TextArea
            rows={3}
            value={feedbackComment}
            onChange={(e) => setFeedbackComment(e.target.value)}
            placeholder="说明您希望改进的具体要求..."
            className="text-xs rounded-xl"
          />
        </div>
      </Modal>
    </div>
  );
};
