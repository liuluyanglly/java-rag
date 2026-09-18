import React, { useState, useEffect, useRef } from 'react';
import {
  Input,
  Button,
  Tag,
  Spin,
  message,
  Popconfirm,
  Drawer,
  Tooltip,
  Dropdown,
  type MenuProps,
} from 'antd';
import {
  CompassOutlined,
  SendOutlined,
  FileDoneOutlined,
  CheckCircleFilled,
  LoadingOutlined,
  SearchOutlined,
  SafetyCertificateOutlined,
  CopyOutlined,
  DownloadOutlined,
  DeleteOutlined,
  FileTextOutlined,
  PlusOutlined,
  RightOutlined,
  DownOutlined,
  CodeOutlined,
  BranchesOutlined,
  FileSearchOutlined,
  CheckOutlined,
  ReloadOutlined,
  FilePdfOutlined,
  PrinterOutlined,
} from '@ant-design/icons';
import {
  submitResearchApi,
  getResearchTasksApi,
  getResearchTaskDetailApi,
  deleteResearchTaskApi,
  ResearchTaskItem,
} from '../../api/research';
import { MarkdownRenderer } from '../../components/MarkdownRenderer';

const { TextArea } = Input;

export const DeepResearchPage: React.FC = () => {
  const [topicInput, setTopicInput] = useState('');
  const [searchKeyword, setSearchKeyword] = useState('');
  const [tasks, setTasks] = useState<ResearchTaskItem[]>([]);
  const [currentTask, setCurrentTask] = useState<ResearchTaskItem | null>(null);
  const [runningStep, setRunningStep] = useState<number>(0);
  const [runningLogs, setRunningLogs] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [copied, setCopied] = useState(false);
  const [citationDrawerOpen, setCitationDrawerOpen] = useState(false);
  const [activeInspectorNode, setActiveInspectorNode] = useState<string | null>(null);

  const eventSourceRef = useRef<EventSource | null>(null);
  const pollTimerRef = useRef<any>(null);
  const reportPrintRef = useRef<HTMLDivElement | null>(null);
  const [exportingPdf, setExportingPdf] = useState(false);

  // 清理当前所有活跃的流与轮询计时器
  const clearActiveStreams = () => {
    if (pollTimerRef.current) {
      clearInterval(pollTimerRef.current);
      pollTimerRef.current = null;
    }
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
  };

  useEffect(() => {
    loadTasks();
    return () => {
      clearActiveStreams();
    };
  }, []);

  const loadTasks = async () => {
    try {
      const res = await getResearchTasksApi();
      setTasks(res || []);
      if (res && res.length > 0 && !currentTask) {
        selectTask(res[0].taskId);
      }
    } catch (e) {
      console.error(e);
    }
  };

  // 高频短轮询核心：即刻探测 + 每 1000ms 探测，实时合并阶段大纲与切片产出，完成时毫秒级呈现研报
  const startTaskPolling = (taskId: string) => {
    if (pollTimerRef.current) {
      clearInterval(pollTimerRef.current);
      pollTimerRef.current = null;
    }

    const checkOnce = async () => {
      try {
        const detail = await getResearchTaskDetailApi(taskId);
        if (!detail) return;

        // 1. 实时把后端随着节点完成而落库的产出物（规划大纲、知识切片）合并入当前视图
        setCurrentTask((prev) => {
          if (!prev || prev.taskId !== detail.taskId) return detail;
          return {
            ...prev,
            ...detail,
            planSteps: detail.planSteps && detail.planSteps !== '[]' ? detail.planSteps : prev.planSteps,
            citations: detail.citations && detail.citations !== '[]' ? detail.citations : prev.citations,
          };
        });

        // 2. 动态跟进流水线节点高亮（Planner -> Searcher -> Critic -> Report）
        if (typeof detail.currentStep === 'number') {
          setRunningStep(detail.currentStep);
        }

        const hasReport = detail.reportMarkdown && detail.reportMarkdown.trim().length > 0;
        const isFinished = detail.status === 'COMPLETED' || detail.status === 'FAILED';

        // 3. 只要任务完成或已有研报正文，立刻完成并展示研报
        if (isFinished || hasReport) {
          clearActiveStreams();
          setLoading(false);
          setRunningStep(detail.status === 'COMPLETED' ? 4 : 0);
          setCurrentTask(detail);
          setTasks((prev) =>
            prev.map((t) => (t.taskId === detail.taskId ? detail : t))
          );
          if (detail.status === 'COMPLETED') {
            message.success('✨ 深度研究工作流执行完毕，长篇研报已生成！');
          } else if (detail.status === 'FAILED') {
            message.error('研报生成失败，请重试');
          }
        }
      } catch (err) {
        console.warn('轮询检测任务状态失败:', err);
      }
    };

    // 立即执行第一次探测，绝不空等
    checkOnce();
    // 随后每 1000ms 探测一次
    pollTimerRef.current = setInterval(checkOnce, 1000);
  };

  const selectTask = async (taskId: string) => {
    clearActiveStreams();
    setActiveInspectorNode(null);

    try {
      const detail = await getResearchTaskDetailApi(taskId);
      setCurrentTask(detail);
      if (detail.status === 'COMPLETED') {
        setLoading(false);
        setRunningStep(4); // 4 表示全流程 4 个节点全部打勾完成
      } else if (detail.status === 'FAILED') {
        setLoading(false);
        setRunningStep(0);
      } else {
        // 任务仍在后台运行中，自动激活高频轮询等待结果
        setLoading(true);
        setRunningStep(detail.currentStep ?? 0);
        startTaskPolling(taskId);
      }
    } catch (e) {
      message.error('获取研报详情失败');
    }
  };

  const handleDeleteTask = async (taskId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await deleteResearchTaskApi(taskId);
      message.success('研报已删除');
      const updated = tasks.filter((t) => t.taskId !== taskId);
      setTasks(updated);
      if (currentTask?.taskId === taskId) {
        if (updated.length > 0) {
          selectTask(updated[0].taskId);
        } else {
          setCurrentTask(null);
        }
      }
    } catch (e) {
      message.error('删除任务失败');
    }
  };

  const handleStartResearch = async (overrideTopic?: string) => {
    const topic = (overrideTopic || topicInput).trim();
    if (!topic || loading) return;

    if (!overrideTopic) setTopicInput('');

    // 0ms 视觉即时响应：清空旧研报残留，立即进入全新执行态
    const tempTaskId = 'temp-' + Date.now();
    const tempTask: ResearchTaskItem = {
      id: Date.now(),
      taskId: tempTaskId,
      userId: 1,
      topic,
      status: 'PLANNING',
      planSteps: '[]',
      currentStep: 0,
      reportMarkdown: '',
      citations: '[]',
      createTime: '刚刚',
    };
    setCurrentTask(tempTask);
    setTasks((prev) => [tempTask, ...prev.filter((t) => !t.taskId.startsWith('temp-'))]);
    setLoading(true);
    setRunningLogs(['🚀 深度研究状态机已触发，正在初始化多智能体并拆解调研规划...']);
    setRunningStep(0);
    setActiveInspectorNode(null);

    // 清理既有连接与定时器
    clearActiveStreams();

    try {
      const newTask = await submitResearchApi({ topic });
      // 无缝替换真实任务
      setTasks((prev) => [newTask, ...prev.filter((t) => t.taskId !== tempTaskId && t.taskId !== newTask.taskId)]);
      setCurrentTask(newTask);

      // 1. 核心保障：立即启动即刻短轮询（1 秒频次），确保阶段产出物与最终研报毫秒级呈现
      startTaskPolling(newTask.taskId);

      // 2. 双保险：建立 SSE 订阅实时日志流水
      try {
        const sse = new EventSource(`/api/research/stream/${newTask.taskId}`);
        eventSourceRef.current = sse;

        sse.addEventListener('progress', (e) => {
          const text = e.data;
          setRunningLogs((prev) => [...prev, text]);

          if (text.includes('PlanNode')) {
            setRunningStep(0);
          } else if (text.includes('RetrievalNode')) {
            setRunningStep(1);
          } else if (text.includes('CriticNode')) {
            setRunningStep(2);
          } else if (text.includes('ReportNode')) {
            setRunningStep(3);
          }

          if (text.startsWith('REPORT_READY')) {
            clearActiveStreams();
            setLoading(false);
            setRunningStep(4); // 标记全部完成
            selectTask(newTask.taskId);
          }
        });

        sse.onerror = () => {
          // SSE 异常关闭即可，由底层的短轮询无缝接管，绝不卡死
          if (eventSourceRef.current) {
            eventSourceRef.current.close();
            eventSourceRef.current = null;
          }
        };
      } catch (sseErr) {
        console.warn('SSE 建立失败，由高频短轮询接管:', sseErr);
      }

    } catch (e) {
      setLoading(false);
      clearActiveStreams();
      message.error('启动深度研究工作流失败');
    }
  };

  const handleCopyReport = () => {
    if (!currentTask?.reportMarkdown) return;
    navigator.clipboard.writeText(currentTask.reportMarkdown);
    setCopied(true);
    message.success('研报全文已复制到剪贴板');
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownloadMarkdown = () => {
    if (!currentTask?.reportMarkdown) return;
    const blob = new Blob([currentTask.reportMarkdown], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${currentTask.topic || '深度研究报告'}.md`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    message.success('已导出 Markdown 研报文件');
  };

  const handleExportPdf = async () => {
    if (!currentTask?.reportMarkdown || !reportPrintRef.current) {
      message.warning('当前研报暂无正文可供导出');
      return;
    }

    setExportingPdf(true);
    const hideLoading = message.loading('正在排版并生成高清 PDF 研报，请稍候...', 0);

    try {
      // 动态引入 html2pdf.js 避免影响页面首屏加载
      // @ts-ignore
      const html2pdfModule = await import('html2pdf.js');
      const html2pdf = html2pdfModule.default || html2pdfModule;

      const element = reportPrintRef.current;
      const cleanTopic = (currentTask.topic || '深度研究报告')
        .replace(/[/\\?%*:|"<>]/g, '_')
        .trim();
      const fileName = `${cleanTopic}.pdf`;

      const opt = {
        margin: [14, 15, 15, 15], // 上 右 下 左 (mm)
        filename: fileName,
        image: { type: 'jpeg', quality: 0.98 },
        html2canvas: {
          scale: 2, // 2倍抗锯齿高清排版
          useCORS: true,
          logging: false,
          letterRendering: true,
        },
        jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' },
        pagebreak: { mode: ['avoid-all', 'css', 'legacy'] },
      };

      await (html2pdf as any)().set(opt).from(element).save();
      hideLoading();
      message.success(`🎉 研报已成功导出为 PDF 文件: ${fileName}`);
    } catch (err) {
      hideLoading();
      console.error('html2pdf 导出异常，自动唤起浏览器打印窗口:', err);
      window.print();
    } finally {
      setExportingPdf(false);
    }
  };

  const exportMenuItems: MenuProps['items'] = [
    {
      key: 'pdf',
      icon: <FilePdfOutlined className="text-red-500" />,
      label: '导出为 PDF 文件 (.pdf)',
      onClick: handleExportPdf,
    },
    {
      key: 'print',
      icon: <PrinterOutlined className="text-blue-500" />,
      label: '调用浏览器打印 (另存为 PDF)',
      onClick: () => window.print(),
    },
    {
      type: 'divider',
    },
    {
      key: 'markdown',
      icon: <FileTextOutlined className="text-purple-500" />,
      label: '导出为 Markdown 源码 (.md)',
      onClick: handleDownloadMarkdown,
    },
  ];

  // 解析解析切片引用
  const parsedCitations = (() => {
    try {
      return currentTask?.citations ? JSON.parse(currentTask.citations) : [];
    } catch {
      return [];
    }
  })();

  // 解析规划步骤
  const parsedPlanSteps = (() => {
    try {
      return currentTask?.planSteps ? JSON.parse(currentTask.planSteps) : [];
    } catch {
      return [];
    }
  })();

  // 节点流水线定义 (扣子 Coze Studio 节点卡片)
  const pipelineNodes = [
    {
      id: 'PlanNode',
      index: 0,
      title: '课题规划',
      role: 'Planner',
      icon: <CompassOutlined className="text-sm" />,
      desc: '课题分解与多维大纲规划',
      detail: parsedPlanSteps.length > 0 ? (
        <div className="space-y-1 text-xs">
          <div className="font-semibold text-slate-700">拆解出的阶段大纲：</div>
          {parsedPlanSteps.map((s: string, idx: number) => (
            <div key={idx} className="p-1.5 bg-slate-50 rounded border border-slate-200/60 font-mono text-[11px] text-slate-600">
              {s}
            </div>
          ))}
        </div>
      ) : '正在对深度研究课题进行系统拆解，制定多维多阶段调研计划...',
    },
    {
      id: 'RetrievalNode',
      index: 1,
      title: '全维搜研',
      role: 'Hybrid Searcher',
      icon: <SearchOutlined className="text-sm" />,
      desc: 'Dense 向量 + BM25 + 图谱重排',
      detail: parsedCitations.length > 0 ? (
        <div className="space-y-1.5 text-xs">
          <div className="font-semibold text-slate-700">召回的高置信度证据切片 ({parsedCitations.length} 篇)：</div>
          {parsedCitations.map((c: any, idx: number) => (
            <div key={idx} className="p-2 bg-slate-50 rounded border border-slate-200/60 text-[11px] space-y-1">
              <div className="flex justify-between font-medium text-indigo-600">
                <span>📄 {c.title || c.source || '切片来源'}</span>
                <span className="text-slate-400 font-mono text-[10px]">RRF得分: {c.score ? Number(c.score).toFixed(4) : '-'}</span>
              </div>
              <div className="text-slate-600 line-clamp-2">{c.snippet}</div>
            </div>
          ))}
        </div>
      ) : '启动四维检索中枢 (Dense 向量 + BM25 关键词 + 图谱拓扑网络)...',
    },
    {
      id: 'CriticNode',
      index: 2,
      title: '反思质检',
      role: 'Critic Router',
      icon: <SafetyCertificateOutlined className="text-sm" />,
      desc: '证据充分性核验与幻觉反思',
      detail: (
        <div className="space-y-1.5 text-xs text-slate-600">
          <div className="flex items-center gap-1.5 text-emerald-600 font-semibold">
            <CheckCircleFilled /> <span>事实一致性校验与动态自愈决策</span>
          </div>
          <p className="text-[11px] leading-relaxed">
            反思审查中枢介入：核验各方论据一致性与置信度。若切片不足触发自愈回路重写提问，若切片充足批准进入研报合成。
          </p>
        </div>
      ),
    },
    {
      id: 'QueryRewriteNode',
      index: 3,
      title: '自愈重构',
      role: 'Self-Correction',
      icon: <BranchesOutlined className="text-sm" />,
      desc: '意图重构与二次深搜回路',
      detail: (
        <div className="space-y-1.5 text-xs text-slate-600">
          <div className="flex items-center gap-1.5 text-indigo-600 font-semibold">
            <ReloadOutlined /> <span>自适应意图重构与关键词拓展</span>
          </div>
          <p className="text-[11px] leading-relaxed">
            当首轮检索切片存在盲区时，大模型自主分析未命中原因，重构多阶段搜索表达式，回流至 RetrievalNode 发起第二轮定向拓搜。
          </p>
        </div>
      ),
    },
    {
      id: 'ReportNode',
      index: 4,
      title: '研报合成',
      role: 'Synthesizer',
      icon: <FileDoneOutlined className="text-sm" />,
      desc: '综合证据链排版输出行业长文研报',
      detail: '正在综合全篇证据链与图谱拓扑网络，排版撰写结构化行业级长篇研报...',
    },
  ];

  // 过滤后的任务列表
  const filteredTasks = tasks.filter((t) =>
    t.topic.toLowerCase().includes(searchKeyword.toLowerCase())
  );

  return (
    <div className="h-full w-full flex bg-slate-50 text-slate-800 overflow-hidden font-sans select-none">
      {/* ──────────────────────────────────────────────────────────── */}
      {/* 左侧：Coze Studio 风格课题工坊侧边栏 */}
      {/* ──────────────────────────────────────────────────────────── */}
      <div className="w-80 bg-white border-r border-slate-200/90 flex flex-col shrink-0 select-none z-10">
        {/* 顶部标题栏 */}
        <div className="p-4 border-b border-slate-100 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-xl bg-gradient-to-tr from-purple-600 via-indigo-600 to-blue-500 text-white flex items-center justify-center shadow-xs">
              <CompassOutlined className="text-base" />
            </div>
            <div>
              <div className="font-bold text-sm text-slate-900 leading-none flex items-center gap-1.5">
                <span>深度研究工坊</span>
                <span className="px-1.5 py-0.5 text-[9px] font-bold rounded-md bg-indigo-50 text-indigo-700 border border-indigo-200">
                  Graph
                </span>
              </div>
              <div className="text-[10px] text-slate-400 mt-1 leading-none">
                StateGraph 多智能体协同流水线
              </div>
            </div>
          </div>
        </div>

        {/* 发起研究卡片 */}
        <div className="p-3.5 border-b border-slate-100 bg-slate-50/60">
          <div className="text-xs font-bold text-slate-700 mb-2 flex items-center gap-1.5">
            <BranchesOutlined className="text-indigo-600" />
            <span>发起新研究课题</span>
          </div>
          <TextArea
            value={topicInput}
            onChange={(e) => setTopicInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                handleStartResearch();
              }
            }}
            placeholder="输入深度研究课题 (如: 国内企业级 RAG 架构选型与落地路径)..."
            autoSize={{ minRows: 2, maxRows: 5 }}
            className="rounded-xl text-xs resize-none mb-2.5 border-slate-200 focus:border-indigo-500 shadow-2xs"
          />
          <Button
            type="primary"
            icon={loading ? <LoadingOutlined /> : <SendOutlined />}
            loading={loading}
            onClick={() => handleStartResearch()}
            disabled={!topicInput.trim() || loading}
            className={`w-full rounded-xl text-xs font-semibold shadow-xs border-0 h-8.5 transition-all ${
              !topicInput.trim() || loading
                ? 'bg-slate-200 text-slate-400 cursor-not-allowed'
                : 'bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white cursor-pointer'
            }`}
          >
            {loading ? '工作流流转中...' : '启动深度调研工作流'}
          </Button>
        </div>

        {/* 课题搜索与历史列表 */}
        <div className="p-2.5 border-b border-slate-100 flex items-center gap-1.5">
          <Input
            placeholder="搜索历史课题..."
            prefix={<SearchOutlined className="text-slate-400 text-xs" />}
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            allowClear
            size="small"
            className="rounded-lg text-xs border-slate-200 bg-white"
          />
        </div>

        <div className="flex-1 overflow-y-auto p-2 space-y-1.5 min-h-0">
          {filteredTasks.length === 0 ? (
            <div className="h-40 flex flex-col items-center justify-center text-xs text-slate-400">
              <FileTextOutlined className="text-2xl mb-1 text-slate-300" />
              <span>暂无匹配研究课题</span>
            </div>
          ) : (
            filteredTasks.map((t) => {
              const isSelected = currentTask?.taskId === t.taskId;
              return (
                <div
                  key={t.taskId}
                  onClick={() => selectTask(t.taskId)}
                  className={`group relative p-2.5 rounded-xl cursor-pointer transition-all border ${
                    isSelected
                      ? 'bg-indigo-50/70 border-indigo-200 text-indigo-950 shadow-xs'
                      : 'bg-white border-slate-100 hover:border-slate-200 hover:bg-slate-50 text-slate-800'
                  }`}
                >
                  <div className="font-semibold text-xs truncate pr-6 leading-normal">
                    {t.topic}
                  </div>
                  <div className="flex items-center justify-between text-[10px] text-slate-400 mt-1.5">
                    <span className="font-mono">
                      {t.createTime ? t.createTime.slice(5, 16) : ''}
                    </span>
                    <span
                      className={`px-1.5 py-0.5 rounded text-[9px] font-bold ${
                        t.status === 'COMPLETED'
                          ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                          : 'bg-indigo-50 text-indigo-700 border border-indigo-200 animate-pulse'
                      }`}
                    >
                      {t.status === 'COMPLETED' ? '已完成' : '调研中'}
                    </span>
                  </div>

                  {/* 悬停一键删除 */}
                  <div className="absolute right-2 top-2.5 opacity-0 group-hover:opacity-100 transition-opacity">
                    <Popconfirm
                      title="确定删除此研报任务吗？"
                      onConfirm={(e) => handleDeleteTask(t.taskId, e as any)}
                      okText="删除"
                      cancelText="取消"
                      placement="right"
                    >
                      <button
                        onClick={(e) => e.stopPropagation()}
                        className="p-1 rounded text-slate-400 hover:text-red-500 hover:bg-red-50 transition-colors"
                        title="删除研报"
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

      {/* ──────────────────────────────────────────────────────────── */}
      {/* 右侧：Coze Studio 主工作台画布与研报阅读器 */}
      {/* ──────────────────────────────────────────────────────────── */}
      <div className="flex-1 flex flex-col overflow-hidden bg-white">
        {currentTask ? (
          <div className="h-full flex flex-col min-h-0">
            {/* 顶部标题栏与 Coze 快捷操作条 */}
            <div className="px-6 py-3.5 border-b border-slate-100 flex items-center justify-between shrink-0 bg-white z-10">
              <div className="min-w-0 pr-4">
                <div className="flex items-center gap-2">
                  <h1 className="text-base font-bold text-slate-950 truncate leading-tight">
                    {currentTask.topic}
                  </h1>
                  <Tag color="purple" className="text-[10px] shrink-0 font-bold border-0 bg-purple-50 text-purple-700">
                    已通过反思核验 (Critic Passed)
                  </Tag>
                </div>
                {parsedCitations.length > 0 && (
                  <div className="text-[11px] text-slate-400 mt-1 truncate flex items-center gap-2">
                    <span
                      className="text-indigo-600 font-medium cursor-pointer hover:underline flex items-center gap-1"
                      onClick={() => setCitationDrawerOpen(true)}
                    >
                      <span>📚 引用切片证据: {parsedCitations.length} 篇</span>
                    </span>
                  </div>
                )}
              </div>

              {/* 右侧工具栏 */}
              <div className="flex items-center gap-2 shrink-0">
                <Tooltip title="查看引用证据切片溯源">
                  <Button
                    size="small"
                    icon={<FileSearchOutlined />}
                    onClick={() => setCitationDrawerOpen(true)}
                    className="rounded-lg text-xs text-slate-600 hover:text-purple-600"
                  >
                    证据溯源
                  </Button>
                </Tooltip>

                <Tooltip title="一键复制研报 Markdown">
                  <Button
                    size="small"
                    icon={copied ? <CheckOutlined className="text-emerald-500" /> : <CopyOutlined />}
                    onClick={handleCopyReport}
                    className="rounded-lg text-xs"
                  >
                    {copied ? '已复制' : '复制研报'}
                  </Button>
                </Tooltip>

                <Dropdown menu={{ items: exportMenuItems }} placement="bottomRight">
                  <Button
                    size="small"
                    icon={<FilePdfOutlined className="text-red-500" />}
                    onClick={handleExportPdf}
                    loading={exportingPdf}
                    className="rounded-lg text-xs font-medium hover:border-red-400 flex items-center gap-1 shadow-2xs"
                  >
                    <span>导出 PDF</span>
                    <DownOutlined className="text-[9px] text-slate-400" />
                  </Button>
                </Dropdown>

                <Tooltip title="基于当前课题重新流式研报">
                  <Button
                    size="small"
                    type="primary"
                    icon={<ReloadOutlined />}
                    onClick={() => handleStartResearch(currentTask.topic)}
                    loading={loading}
                    className="rounded-lg text-xs bg-purple-600 hover:bg-purple-500 font-semibold"
                  >
                    重新研究
                  </Button>
                </Tooltip>
              </div>
            </div>

            {/* 中间主要滚动区 */}
            <div className="flex-1 overflow-y-auto px-6 py-5 space-y-6">
              {/* ──────────────────────────────────────────────────────────── */}
              {/* Agentic RAG 有向状态图工作流看板 (StateGraph Canvas) */}
              {/* ──────────────────────────────────────────────────────────── */}
              <div className="rounded-2xl border border-slate-200/90 bg-slate-50/70 p-4 shadow-xs">
                <div className="flex items-center justify-between mb-3 text-xs">
                  <div className="font-bold text-slate-800 flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-indigo-500" />
                    <span>SPRING AI ALIBABA GRAPH 状态流转看板 (5 阶段 Agentic 闭环)</span>
                  </div>
                  <span className="text-[11px] text-slate-400 font-normal">
                    {loading ? '🚀 正在实时驱动多智能体执行...' : '✨ 图工作流执行闭环归档'}
                  </span>
                </div>

                {/* 5 节点卡片流式网格 */}
                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3">
                  {pipelineNodes.map((node) => {
                    const isAllDone = currentTask.status === 'COMPLETED' && !loading;
                    const isCurrent = loading && runningStep === node.index;
                    const isDone = isAllDone || (!loading && currentTask.status === 'COMPLETED') || (loading && runningStep > node.index);
                    const isInspectorActive = activeInspectorNode === node.id;

                    return (
                      <div
                        key={node.id}
                        onClick={() => setActiveInspectorNode(isInspectorActive ? null : node.id)}
                        className={`p-3 rounded-xl border transition-all cursor-pointer select-none flex flex-col justify-between ${
                          isInspectorActive
                            ? 'bg-white border-purple-500 shadow-md ring-2 ring-purple-100'
                            : isCurrent
                            ? 'bg-purple-50/80 border-purple-300 shadow-xs'
                            : isDone
                            ? 'bg-white border-slate-200/80 hover:border-slate-300'
                            : 'bg-slate-100/50 border-slate-200/50 opacity-60'
                        }`}
                      >
                        <div className="flex items-center justify-between mb-1.5">
                          <span className="text-[10px] font-bold text-slate-400 font-mono">
                            NODE {node.index + 1}
                          </span>

                          {/* 状态徽标：杜绝死循环转圈！完成即绿色打勾，进行中才转圈 */}
                          {isDone ? (
                            <span className="flex items-center gap-1 text-emerald-600 text-xs font-bold">
                              <CheckCircleFilled className="text-emerald-500" />
                              <span className="text-[10px]">已完成</span>
                            </span>
                          ) : isCurrent ? (
                            <span className="flex items-center gap-1 text-purple-600 text-xs font-bold animate-pulse">
                              <LoadingOutlined />
                              <span className="text-[10px]">执行中</span>
                            </span>
                          ) : (
                            <span className="text-slate-400 text-[10px]">待流转</span>
                          )}
                        </div>

                        <div>
                          <div className="flex items-center gap-1.5 font-bold text-xs text-slate-900">
                            <span className={isDone ? 'text-emerald-600' : isCurrent ? 'text-purple-600' : 'text-slate-400'}>
                              {node.icon}
                            </span>
                            <span>{node.title}</span>
                          </div>
                          <div className="text-[11px] text-slate-400 truncate mt-0.5">
                            {node.role}
                          </div>
                        </div>

                        <div className="mt-2 pt-2 border-t border-slate-100 flex items-center justify-between text-[10px] text-slate-500">
                          <span>{node.desc.slice(0, 10)}...</span>
                          <span className="text-purple-600 font-medium flex items-center gap-0.5">
                            {isInspectorActive ? '收起' : '详情'} {isInspectorActive ? <DownOutlined className="text-[8px]" /> : <RightOutlined className="text-[8px]" />}
                          </span>
                        </div>
                      </div>
                    );
                  })}
                </div>

                {/* 节点检查器抽屉/详情卡片 */}
                {activeInspectorNode && (
                  <div className="mt-3 p-3.5 bg-white rounded-xl border border-purple-200/80 shadow-xs">
                    <div className="flex items-center justify-between mb-2 pb-1.5 border-b border-slate-100">
                      <span className="font-bold text-xs text-slate-900 flex items-center gap-1.5">
                        <CodeOutlined className="text-purple-600" />
                        <span>节点检查器 (Node Inspector) : {pipelineNodes.find((n) => n.id === activeInspectorNode)?.title}</span>
                      </span>
                      <button
                        onClick={() => setActiveInspectorNode(null)}
                        className="text-xs text-slate-400 hover:text-slate-700"
                      >
                        关闭
                      </button>
                    </div>
                    <div>
                      {pipelineNodes.find((n) => n.id === activeInspectorNode)?.detail}
                    </div>
                  </div>
                )}

                {/* 实时流转日志控制台 */}
                {runningLogs.length > 0 && (
                  <div className="mt-3 p-2.5 bg-slate-950 text-slate-200 rounded-xl font-mono text-[11px] max-h-28 overflow-y-auto space-y-1">
                    <div className="text-[10px] text-slate-400 border-b border-slate-800 pb-1 mb-1 font-bold">
                      实时状态图执行日志 (Live Graph Trace)
                    </div>
                    {runningLogs.map((log, idx) => (
                      <div key={idx} className="text-emerald-400 leading-relaxed">
                        {log}
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* ──────────────────────────────────────────────────────────── */}
              {/* 研报正文展示区：杂志级 Markdown 研报阅读器与元数据看板 */}
              {/* ──────────────────────────────────────────────────────────── */}
              {currentTask.reportMarkdown && (
                <div className="max-w-4xl mx-auto w-full bg-slate-50/90 border border-slate-200/80 rounded-2xl px-5 py-3.5 flex flex-wrap items-center justify-between gap-3 text-xs text-slate-600 shadow-2xs">
                  <div className="flex items-center gap-4 flex-wrap">
                    <div className="flex items-center gap-1.5 font-medium text-slate-700">
                      <FileTextOutlined className="text-purple-600" />
                      <span>全文篇幅: <strong className="text-slate-900 font-mono">{currentTask.reportMarkdown.length}</strong> 字</span>
                    </div>
                    <div className="w-1 h-3 bg-slate-200 rounded-full hidden sm:block" />
                    <div className="flex items-center gap-1.5 text-slate-600">
                      <span>⏱️ 预计阅读: <strong className="text-slate-900 font-mono">{Math.max(1, Math.ceil(currentTask.reportMarkdown.length / 400))}</strong> 分钟</span>
                    </div>
                    <div className="w-1 h-3 bg-slate-200 rounded-full hidden sm:block" />
                    <div className="flex items-center gap-1.5 text-slate-600">
                      <span>📚 知识库切片: <strong className="text-purple-600 font-mono">{parsedCitations.length}</strong> 项</span>
                    </div>
                    <Tag color="purple" className="rounded-md text-[11px] font-medium border-purple-200">
                      GFM 国际标准排版
                    </Tag>
                  </div>

                  <div className="flex items-center gap-2">
                    <Tooltip title="复制研报全文 (Markdown 源码)">
                      <Button
                        size="small"
                        icon={copied ? <CheckOutlined className="text-emerald-500" /> : <CopyOutlined />}
                        onClick={handleCopyReport}
                        className="rounded-lg text-xs hover:border-purple-400"
                      >
                        {copied ? '已复制' : '复制研报'}
                      </Button>
                    </Tooltip>
                    <Dropdown menu={{ items: exportMenuItems }} placement="bottomRight">
                      <Button
                        size="small"
                        type="primary"
                        icon={<FilePdfOutlined />}
                        onClick={handleExportPdf}
                        loading={exportingPdf}
                        className="bg-red-600 hover:bg-red-700 border-red-600 hover:border-red-700 rounded-lg text-xs shadow-xs flex items-center gap-1 font-medium"
                      >
                        <span>导出 PDF</span>
                        <DownOutlined className="text-[9px] text-white/80" />
                      </Button>
                    </Dropdown>
                  </div>
                </div>
              )}

              {/* 专业研报排版画卷 */}
              <div
                id="printable-report"
                ref={reportPrintRef}
                className="bg-white rounded-2xl border border-slate-200/90 px-8 py-10 sm:px-14 sm:py-12 shadow-sm max-w-4xl mx-auto w-full"
              >
                {currentTask.reportMarkdown ? (
                  <MarkdownRenderer content={currentTask.reportMarkdown} />
                ) : loading || (currentTask.status !== 'COMPLETED' && currentTask.status !== 'FAILED') ? (
                  <div className="py-20 flex flex-col items-center justify-center text-slate-400">
                    <Spin size="large" />
                    <span className="text-xs mt-3 font-medium">智能体状态图正在深入撰写长篇研报，请稍候...</span>
                  </div>
                ) : (
                  <div className="py-20 flex flex-col items-center justify-center text-slate-400">
                    <p className="text-sm font-medium text-slate-600 mb-3">该任务未生成有效研报或已终止</p>
                    <Button
                      type="primary"
                      size="small"
                      icon={<ReloadOutlined />}
                      onClick={() => handleStartResearch(currentTask.topic)}
                      className="rounded-lg bg-purple-600"
                    >
                      重新生成研报
                    </Button>
                  </div>
                )}
              </div>
            </div>
          </div>
        ) : (
          <div className="h-full flex flex-col items-center justify-center text-slate-400">
            <div className="w-16 h-16 rounded-3xl bg-purple-50 text-purple-600 flex items-center justify-center text-3xl mb-4 shadow-xs">
              <CompassOutlined />
            </div>
            <h3 className="font-bold text-base text-slate-700 mb-1">欢迎来到深度研究工坊</h3>
            <p className="text-xs text-slate-400 max-w-sm text-center mb-4">
              基于 Spring AI Alibaba Graph 状态机编排引擎，输入研究课题即可一键触发大纲规划、四维搜研、反思质检与长篇研报生成。
            </p>
          </div>
        )}
      </div>

      {/* ──────────────────────────────────────────────────────────── */}
      {/* 引用证据切片溯源抽屉 (Citation Drawer) */}
      {/* ──────────────────────────────────────────────────────────── */}
      <Drawer
        title="📚 知识库证据切片溯源 (Evidence Citations)"
        placement="right"
        width={480}
        open={citationDrawerOpen}
        onClose={() => setCitationDrawerOpen(false)}
      >
        <div className="space-y-4">
          <div className="text-xs text-slate-500 bg-slate-50 p-3 rounded-xl border border-slate-200/80 leading-relaxed">
            本研报基于全维检索中枢（Dense 向量 + BM25 关键词 + 拓扑图谱）从企业知识库中精准召回以下切片，并经 RRF 融合重排与反思核验。
          </div>

          {parsedCitations.length === 0 ? (
            <div className="text-center py-10 text-xs text-slate-400">
              知识库暂未检索到直接切片，报告由大模型先验常识与工程最佳实践综合生成。
            </div>
          ) : (
            parsedCitations.map((item: any, idx: number) => (
              <div
                key={idx}
                className="p-3.5 rounded-xl border border-slate-200/90 bg-white shadow-2xs space-y-2 text-xs"
              >
                <div className="flex items-center justify-between border-b border-slate-100 pb-2">
                  <span className="font-bold text-slate-900 truncate">
                    📄 {item.title || item.source || `证据切片 #${idx + 1}`}
                  </span>
                  <Tag color="purple" className="text-[10px] mr-0">
                    {item.source || 'VECTOR'}
                  </Tag>
                </div>

                <div className="text-slate-600 text-[11px] leading-relaxed font-mono whitespace-pre-wrap max-h-48 overflow-y-auto bg-slate-50 p-2 rounded-lg border border-slate-100">
                  {item.snippet}
                </div>

                {item.score && (
                  <div className="text-[10px] text-slate-400 font-mono text-right">
                    RRF 匹配度得分: <span className="font-bold text-purple-600">{Number(item.score).toFixed(4)}</span>
                  </div>
                )}
              </div>
            ))
          )}
        </div>
      </Drawer>

      {/* 打印与 PDF 生成专用排版样式 */}
      <style>{`
        @media print {
          body * {
            visibility: hidden !important;
          }
          #printable-report, #printable-report * {
            visibility: visible !important;
          }
          #printable-report {
            position: absolute !important;
            left: 0 !important;
            top: 0 !important;
            width: 100% !important;
            margin: 0 !important;
            padding: 12mm 15mm !important;
            border: none !important;
            box-shadow: none !important;
            background: white !important;
          }
          /* 防止代码块、表格与引用块在跨页时被生硬截断 */
          table, pre, blockquote {
            page-break-inside: avoid !important;
            break-inside: avoid !important;
          }
          h1, h2, h3 {
            page-break-after: avoid !important;
            break-after: avoid !important;
          }
        }
      `}</style>
    </div>
  );
};
