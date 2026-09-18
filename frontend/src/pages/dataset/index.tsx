import React, { useState, useEffect } from 'react';
import {
  Card,
  Button,
  Table,
  Tag,
  Modal,
  Form,
  Input,
  InputNumber,
  Switch,
  Select,
  Upload,
  Drawer,
  message,
  Popconfirm,
  Badge,
  Tooltip,
  Spin,
  Empty,
  Tabs,
  Slider,
  Pagination,
} from 'antd';
import {
  PlusOutlined,
  UploadOutlined,
  FileTextOutlined,
  DeleteOutlined,
  ReloadOutlined,
  EyeOutlined,
  SafetyCertificateOutlined,
  ApartmentOutlined,
  InboxOutlined,
  SearchOutlined,
  DatabaseOutlined,
  FilePdfOutlined,
  FileWordOutlined,
  FileMarkdownOutlined,
  AimOutlined,
  ThunderboltOutlined,
  CopyOutlined,
} from '@ant-design/icons';
import { MarkdownRenderer } from '../../components/MarkdownRenderer';
import {
  getDatasetListApi,
  createDatasetApi,
  deleteDatasetApi,
  getDocumentListApi,
  getDocumentChunksApi,
  reindexDocumentApi,
  deleteDocumentApi,
  testDatasetHitApi,
  DatasetItem,
  DocumentItem,
  DocumentChunkItem,
} from '../../api/dataset';
import { TOKEN_HEADER } from '../../api/request';

export const DatasetPage: React.FC = () => {
  const [datasets, setDatasets] = useState<DatasetItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchName, setSearchName] = useState('');
  const [modalVisible, setModalVisible] = useState(false);
  const [form] = Form.useForm();

  // 文档抽屉状态
  const [selectedDataset, setSelectedDataset] = useState<DatasetItem | null>(null);
  const [drawerVisible, setDrawerVisible] = useState(false);
  const [drawerActiveTab, setDrawerActiveTab] = useState<'docs' | 'hittest'>('docs');
  const [documents, setDocuments] = useState<DocumentItem[]>([]);
  const [docsLoading, setDocsLoading] = useState(false);

  // 召回测试器状态 (Coze Hit-Testing)
  const [testQuery, setTestQuery] = useState('');
  const [testTopK, setTestTopK] = useState(4);
  const [hitTesting, setHitTesting] = useState(false);
  const [hitTestResults, setHitTestResults] = useState<any[]>([]);

  // 切片查看模态框
  const [chunkModalVisible, setChunkModalVisible] = useState(false);
  const [chunks, setChunks] = useState<DocumentChunkItem[]>([]);
  const [chunkLoading, setChunkLoading] = useState(false);
  const [activeDocName, setActiveDocName] = useState('');
  const [activeDocId, setActiveDocId] = useState<number | null>(null);
  const [chunkPageNum, setChunkPageNum] = useState(1);
  const [chunkPageSize, setChunkPageSize] = useState(20);
  const [chunkTotal, setChunkTotal] = useState(0);

  useEffect(() => {
    loadDatasets();
  }, []);

  const loadDatasets = async () => {
    try {
      setLoading(true);
      const res = await getDatasetListApi({ pageNum: 1, pageSize: 50, name: searchName || undefined });
      setDatasets(res.records || []);
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = async (values: any) => {
    try {
      await createDatasetApi(values);
      message.success('知识库创建成功！');
      setModalVisible(false);
      form.resetFields();
      loadDatasets();
    } catch (e) {}
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteDatasetApi(id);
      message.success('知识库已删除');
      loadDatasets();
    } catch (e) {}
  };

  const openDocumentsDrawer = async (dataset: DatasetItem, defaultTab: 'docs' | 'hittest' = 'docs') => {
    setSelectedDataset(dataset);
    setDrawerActiveTab(defaultTab);
    setDrawerVisible(true);
    setHitTestResults([]);
    setTestQuery('');
    loadDocuments(dataset.id);
  };

  const loadDocuments = async (datasetId: number) => {
    try {
      setDocsLoading(true);
      const res = await getDocumentListApi({ datasetId, pageNum: 1, pageSize: 50 });
      setDocuments(res.records || []);
    } finally {
      setDocsLoading(false);
    }
  };

  // 运行召回命中测试 (仿扣子 Hit Testing)
  const handleRunHitTest = async () => {
    if (!selectedDataset || !testQuery.trim()) {
      message.warning('请输入测试问题');
      return;
    }
    setHitTesting(true);
    try {
      const res = await testDatasetHitApi(selectedDataset.id, testQuery.trim(), testTopK);
      setHitTestResults(res);
      if (res.length === 0) {
        message.info('未召回到相关切片段落，可调整问题或增加分块覆盖');
      } else {
        message.success(`成功召回 ${res.length} 个相关切片段落！`);
      }
    } catch (e) {
      message.error('召回测试执行异常');
    } finally {
      setHitTesting(false);
    }
  };

  const loadChunkData = async (documentId: number, page: number, size: number) => {
    setChunkLoading(true);
    try {
      const res = await getDocumentChunksApi({ documentId, pageNum: page, pageSize: size });
      setChunks(res.records || []);
      setChunkTotal(res.total || 0);
    } catch (e) {
      message.error('加载切片数据失败');
    } finally {
      setChunkLoading(false);
    }
  };

  const viewChunks = (doc: DocumentItem) => {
    setActiveDocName(doc.name);
    setActiveDocId(doc.id);
    setChunkPageNum(1);
    setChunkModalVisible(true);
    loadChunkData(doc.id, 1, chunkPageSize);
  };

  const handleChunkPageChange = (page: number, size?: number) => {
    if (!activeDocId) return;
    const newSize = size || chunkPageSize;
    setChunkPageNum(page);
    setChunkPageSize(newSize);
    loadChunkData(activeDocId, page, newSize);
  };

  const handleReindex = async (docId: number) => {
    try {
      await reindexDocumentApi(docId);
      message.success('已触发异步重新切片任务');
      if (selectedDataset) loadDocuments(selectedDataset.id);
    } catch (e) {}
  };

  const handleDeleteDoc = async (docId: number) => {
    try {
      await deleteDocumentApi(docId);
      message.success('文档已删除');
      if (selectedDataset) {
        loadDocuments(selectedDataset.id);
        loadDatasets();
      }
    } catch (e) {}
  };

  const getFileIcon = (fileName: string) => {
    const ext = fileName.split('.').pop()?.toLowerCase();
    if (ext === 'pdf') return <FilePdfOutlined className="text-red-500 text-base" />;
    if (ext === 'doc' || ext === 'docx') return <FileWordOutlined className="text-blue-500 text-base" />;
    if (ext === 'md') return <FileMarkdownOutlined className="text-emerald-500 text-base" />;
    return <FileTextOutlined className="text-indigo-500 text-base" />;
  };

  const totalDocs = datasets.reduce((acc, cur) => acc + (cur.docCount || 0), 0);
  const totalChunks = datasets.reduce((acc, cur) => acc + (cur.chunkCount || 0), 0);

  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      {/* 顶部标题与数据概览 Banner */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 p-6 rounded-2xl bg-gradient-to-r from-indigo-900/40 via-purple-900/20 to-slate-900/40 border border-indigo-500/20 backdrop-blur-xl">
        <div>
          <div className="flex items-center gap-2.5">
            <span className="p-2 rounded-xl bg-indigo-600/20 text-indigo-400 border border-indigo-500/30 text-xl">
              <DatabaseOutlined />
            </span>
            <h1 className="text-xl font-bold text-slate-900 dark:text-white tracking-tight">
              企业知识库中枢 (RAG Datasets)
            </h1>
          </div>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1 max-w-2xl leading-relaxed">
            基于 PostgreSQL pgvector 构建，支持【逻辑隔离】与【物理隔离】双轨架构，集成知识库召回测试沙箱与自动化切片引擎。
          </p>
        </div>

        {/* 统计指标与新建按钮 */}
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-6 px-4 py-2 rounded-xl bg-white/50 dark:bg-black/20 border border-slate-200/60 dark:border-slate-800">
            <div className="text-center">
              <div className="text-[10px] text-slate-400 font-medium">知识库</div>
              <div className="text-base font-bold text-indigo-600 dark:text-indigo-400 font-mono">
                {datasets.length}
              </div>
            </div>
            <div className="w-[1px] h-6 bg-slate-200 dark:bg-slate-800" />
            <div className="text-center">
              <div className="text-[10px] text-slate-400 font-medium">文档总数</div>
              <div className="text-base font-bold text-emerald-600 dark:text-emerald-400 font-mono">
                {totalDocs}
              </div>
            </div>
            <div className="w-[1px] h-6 bg-slate-200 dark:bg-slate-800" />
            <div className="text-center">
              <div className="text-[10px] text-slate-400 font-medium">向量切片</div>
              <div className="text-base font-bold text-purple-600 dark:text-purple-400 font-mono">
                {totalChunks}
              </div>
            </div>
          </div>

          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setModalVisible(true)}
            className="h-10 px-5 rounded-xl bg-gradient-to-r from-indigo-600 to-violet-600 hover:from-indigo-500 hover:to-violet-500 border-none font-medium shadow-md shadow-indigo-500/20 text-xs"
          >
            新建知识库
          </Button>
        </div>
      </div>

      {/* 搜索与过滤 */}
      <div className="flex items-center justify-between">
        <Input
          prefix={<SearchOutlined className="text-slate-400 text-xs" />}
          placeholder="按知识库名称模糊搜索..."
          value={searchName}
          onChange={(e) => setSearchName(e.target.value)}
          onPressEnter={loadDatasets}
          className="max-w-xs rounded-xl bg-white dark:bg-slate-900 border-slate-200 dark:border-slate-800 text-xs"
          allowClear
        />
      </div>

      {/* 知识库卡片网格 (Coze 扣子质感) */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
        {datasets.map((d: any) => {
          const isPhysical = d.isolationType === 'PHYSICAL';

          return (
            <div
              key={d.id}
              className={`rounded-2xl border transition-all duration-200 p-5 bg-white dark:bg-[#121622] hover:shadow-xl hover:-translate-y-0.5 flex flex-col justify-between ${
                isPhysical
                  ? 'border-purple-500/40 dark:border-purple-900/60 shadow-purple-500/5'
                  : 'border-slate-200/80 dark:border-slate-800/80 shadow-slate-200/50 dark:shadow-none'
              }`}
            >
              {/* 卡片头部 */}
              <div>
                <div className="flex items-start justify-between gap-3 mb-3">
                  <div className="flex items-center gap-3">
                    <div
                      className={`w-12 h-12 rounded-2xl flex items-center justify-center font-bold text-2xl shrink-0 shadow-inner ${
                        isPhysical
                          ? 'bg-purple-500/10 text-purple-600 border border-purple-500/20'
                          : 'bg-emerald-500/10 text-emerald-600 border border-emerald-500/20'
                      }`}
                    >
                      {isPhysical ? '🛡️' : '📚'}
                    </div>
                    <div>
                      <h3 className="text-sm font-bold text-slate-900 dark:text-white line-clamp-1">
                        {d.name}
                      </h3>
                      <div className="mt-1">
                        {isPhysical ? (
                          <Tag color="purple" className="text-[10px] m-0 rounded-md font-medium">
                            <SafetyCertificateOutlined className="mr-1" />
                            物理隔离 (专属Schema)
                          </Tag>
                        ) : (
                          <Tag color="blue" className="text-[10px] m-0 rounded-md font-medium">
                            <ApartmentOutlined className="mr-1" />
                            逻辑隔离 (元数据共享)
                          </Tag>
                        )}
                      </div>
                    </div>
                  </div>
                </div>

                <p className="text-xs text-slate-500 dark:text-slate-400 line-clamp-2 leading-relaxed min-h-[36px]">
                  {d.description || '暂无描述信息，该知识库可挂载至智能体提供上下文召回能力。'}
                </p>
              </div>

              {/* 卡片底部属性与操作 */}
              <div className="mt-5 pt-3.5 border-t border-slate-100 dark:border-slate-800/80 flex items-center justify-between">
                <div className="flex items-center gap-3 text-xs text-slate-400 font-mono">
                  <span>📄 {d.docCount || 0} 篇</span>
                  <span>🧩 {d.chunkCount || 0} 切片</span>
                </div>

                <div className="flex items-center gap-1.5">
                  <Tooltip title="输入测试问题，检验知识库向量与关键词命中效果">
                    <Button
                      type="default"
                      size="small"
                      icon={<AimOutlined className="text-purple-500" />}
                      onClick={() => openDocumentsDrawer(d, 'hittest')}
                      className="text-xs rounded-lg border-purple-200 dark:border-purple-800/60 text-purple-600 dark:text-purple-400 hover:!border-purple-400"
                    >
                      召回测试
                    </Button>
                  </Tooltip>

                  <Button
                    type="link"
                    size="small"
                    onClick={() => openDocumentsDrawer(d, 'docs')}
                    className="text-xs font-semibold text-indigo-600 dark:text-indigo-400 hover:text-indigo-500 px-2"
                  >
                    文档 & 上传 →
                  </Button>

                  <Popconfirm
                    title="确定删除此知识库吗？所有切片数据将一并清理。"
                    onConfirm={() => handleDelete(d.id)}
                    okText="删除"
                    cancelText="取消"
                  >
                    <Button
                      type="text"
                      size="small"
                      danger
                      icon={<DeleteOutlined />}
                      className="hover:!bg-red-50 dark:hover:!bg-red-950/40"
                    />
                  </Popconfirm>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* 新增知识库 Modal */}
      <Modal
        title="✨ 新建企业级知识库 (RAG Dataset)"
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        onOk={() => form.submit()}
        width={560}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreate}
          initialValues={{
            embeddingModel: 'text-embedding-3-small',
            chunkSize: 800,
            chunkOverlap: 100,
            isPublic: true,
            isolationType: 'LOGICAL',
            securityLevel: 1,
          }}
          className="pt-2 text-xs"
        >
          <Form.Item name="name" label="知识库名称" rules={[{ required: true, message: '请输入知识库名称' }]}>
            <Input placeholder="例如: 核心技术专利与业务规约库" className="rounded-xl" />
          </Form.Item>

          <Form.Item name="description" label="描述信息">
            <Input.TextArea placeholder="简要描述该知识库覆盖的业务领域与核心文件..." rows={2} className="rounded-xl" />
          </Form.Item>

          <div className="grid grid-cols-2 gap-4">
            <Form.Item name="isolationType" label="安全隔离模式" rules={[{ required: true }]}>
              <Select
                className="rounded-xl"
                options={[
                  { label: '逻辑隔离 (元数据过滤/高效共享)', value: 'LOGICAL' },
                  { label: '物理隔离 (专属Schema/私有算力)', value: 'PHYSICAL' },
                ]}
              />
            </Form.Item>
            <Form.Item name="securityLevel" label="密级标准">
              <Select
                className="rounded-xl"
                options={[
                  { label: '1级 - 公开授权', value: 1 },
                  { label: '2级 - 部门受限', value: 2 },
                  { label: '3级 - 核心机密', value: 3 },
                ]}
              />
            </Form.Item>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <Form.Item name="chunkSize" label="切片大小 (Tokens/字符)">
              <InputNumber className="w-full rounded-xl" min={200} max={2000} />
            </Form.Item>
            <Form.Item name="chunkOverlap" label="重叠字符 (Overlap)">
              <InputNumber className="w-full rounded-xl" min={0} max={300} />
            </Form.Item>
          </div>

          <Form.Item name="isPublic" label="全员开放使用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      {/* 知识库详情与召回测试器 Drawer */}
      <Drawer
        title={
          <div className="flex items-center gap-2">
            <span>📚 知识库管理工作台:</span>
            <Tag color="indigo" className="font-semibold text-xs">
              {selectedDataset?.name || ''}
            </Tag>
          </div>
        }
        width={820}
        open={drawerVisible}
        onClose={() => setDrawerVisible(false)}
      >
        <Tabs
          activeKey={drawerActiveTab}
          onChange={(key) => setDrawerActiveTab(key as any)}
          items={[
            {
              key: 'docs',
              label: (
                <span className="flex items-center gap-1.5">
                  <FileTextOutlined />
                  <span>文档管理与上传 ({documents.length})</span>
                </span>
              ),
              children: (
                <div className="space-y-6 pt-2">
                  {/* 大型拖拽上传区 (纯净单层虚线框) */}
                  <Upload.Dragger
                    action={`/api/document/upload?datasetId=${selectedDataset?.id}`}
                    headers={{ [TOKEN_HEADER]: localStorage.getItem('token') ?? '' }}
                    multiple={true}
                    showUploadList={false}
                    onChange={(info) => {
                      if (info.file.status === 'done') {
                        message.success(`《${info.file.name}》上传成功！后台已自动启动异步解析与切片。`);
                        if (selectedDataset) {
                          loadDocuments(selectedDataset.id);
                          loadDatasets();
                        }
                      } else if (info.file.status === 'error') {
                        message.error(`${info.file.name} 上传失败，请检查文件格式`);
                      }
                    }}
                    style={{
                      borderRadius: '16px',
                      background: 'rgba(99, 102, 241, 0.04)',
                      borderColor: '#a5b4fc',
                      padding: '24px 16px',
                    }}
                  >
                    <p className="text-indigo-600 dark:text-indigo-400 mb-2">
                      <InboxOutlined style={{ fontSize: 38 }} />
                    </p>
                    <p className="text-sm font-bold text-slate-800 dark:text-slate-100">
                      点击或将文档拖拽到此处进行上传与解析
                    </p>
                    <p className="text-xs text-slate-400 mt-1">
                      支持 PDF, Word (.docx), Markdown (.md), TXT 等格式 · 自动提取文本与向量化入库
                    </p>
                  </Upload.Dragger>

                  {/* 文档列表表格 */}
                  <div>
                    <div className="flex items-center justify-between mb-3">
                      <span className="text-xs font-bold text-slate-700 dark:text-slate-300">
                        已入库文档清单
                      </span>
                      <Button
                        size="small"
                        icon={<ReloadOutlined />}
                        onClick={() => selectedDataset && loadDocuments(selectedDataset.id)}
                        className="text-xs rounded-lg"
                      >
                        刷新列表
                      </Button>
                    </div>

                    <Table
                      dataSource={documents}
                      rowKey="id"
                      loading={docsLoading}
                      pagination={{ pageSize: 8 }}
                      className="border border-slate-200 dark:border-slate-800 rounded-xl overflow-hidden text-xs"
                      columns={[
                        {
                          title: '文档名称',
                          dataIndex: 'name',
                          key: 'name',
                          render: (text: string) => (
                            <div className="flex items-center gap-2">
                              {getFileIcon(text)}
                              <span className="font-semibold text-slate-800 dark:text-slate-100 line-clamp-1">
                                {text}
                              </span>
                            </div>
                          ),
                        },
                        {
                          title: '解析状态',
                          dataIndex: 'status',
                          key: 'status',
                          width: 110,
                          render: (status: string) => {
                            if (status === 'COMPLETED')
                              return <Tag color="success">解析完成</Tag>;
                            if (status === 'PARSING')
                              return (
                                <Tag color="processing" icon={<ReloadOutlined spin />}>
                                  向量化中
                                </Tag>
                              );
                            if (status === 'FAILED')
                              return <Tag color="error">解析失败</Tag>;
                            return <Tag color="default">排队等待</Tag>;
                          },
                        },
                        {
                          title: '分块数',
                          dataIndex: 'chunkCount',
                          key: 'chunkCount',
                          width: 80,
                          render: (count: number) => (
                            <span className="font-mono text-indigo-600 dark:text-indigo-400 font-semibold">
                              {count || 0}
                            </span>
                          ),
                        },
                        {
                          title: '上传时间',
                          dataIndex: 'createTime',
                          key: 'createTime',
                          width: 150,
                          render: (time: string) => (
                            <span className="text-[11px] text-slate-400 font-mono whitespace-nowrap">
                              {time ? time.slice(0, 16) : '-'}
                            </span>
                          ),
                        },
                        {
                          title: '操作',
                          key: 'action',
                          width: 170,
                          render: (_: any, record: DocumentItem) => (
                            <div className="flex items-center gap-1.5">
                              <Button
                                size="small"
                                icon={<EyeOutlined />}
                                onClick={() => viewChunks(record)}
                                className="text-xs rounded-md"
                              >
                                切片
                              </Button>
                              <Tooltip title="重新切片与向量化">
                                <Button
                                  size="small"
                                  icon={<ReloadOutlined />}
                                  onClick={() => handleReindex(record.id)}
                                  className="text-xs rounded-md text-slate-600 hover:text-indigo-600"
                                >
                                  重构
                                </Button>
                              </Tooltip>
                              <Popconfirm
                                title="确定删除此文档及其所有向量切片吗？"
                                onConfirm={() => handleDeleteDoc(record.id)}
                                okText="删除"
                                cancelText="取消"
                              >
                                <Button size="small" danger icon={<DeleteOutlined />} className="text-xs rounded-md" />
                              </Popconfirm>
                            </div>
                          ),
                        },
                      ]}
                    />
                  </div>
                </div>
              ),
            },
            {
              key: 'hittest',
              label: (
                <span className="flex items-center gap-1.5">
                  <AimOutlined className="text-purple-500" />
                  <span>召回命中测试器 (Hit Testing)</span>
                </span>
              ),
              children: (
                <div className="space-y-5 pt-2">
                  <div className="p-4 rounded-xl bg-purple-50/50 dark:bg-purple-950/20 border border-purple-200/60 dark:border-purple-900/60 text-xs text-purple-700 dark:text-purple-300 leading-relaxed">
                    🎯 <strong>知识库检索测试沙箱</strong>：输入真实业务问题，实时模拟 Agent 的 RAG 检索流程，直观检验本知识库是否能精准命中相关切片，助您调优知识库颗粒度。
                  </div>

                  {/* 检索输入框 */}
                  <div className="flex gap-2">
                    <Input.TextArea
                      value={testQuery}
                      onChange={(e) => setTestQuery(e.target.value)}
                      placeholder="输入测试问题，例如：企业对于数据库隔离和权限控制有什么具体要求？"
                      rows={2}
                      className="rounded-xl text-xs"
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' && !e.shiftKey) {
                          e.preventDefault();
                          handleRunHitTest();
                        }
                      }}
                    />
                    <Button
                      type="primary"
                      icon={<ThunderboltOutlined />}
                      loading={hitTesting}
                      onClick={handleRunHitTest}
                      className="h-auto px-5 rounded-xl bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 border-none font-medium text-xs"
                    >
                      测试召回
                    </Button>
                  </div>

                  {/* 召回结果列表 */}
                  <div className="space-y-3">
                    <div className="text-xs font-bold text-slate-700 dark:text-slate-300">
                      召回切片结果 ({hitTestResults.length}):
                    </div>

                    {hitTesting ? (
                      <div className="text-center py-10">
                        <Spin tip="正在进行混合语义检索与相关度计算..." />
                      </div>
                    ) : hitTestResults.length === 0 ? (
                      <Empty description="请输入问题并点击【测试召回】进行检验" className="my-10" />
                    ) : (
                      hitTestResults.map((item, idx) => (
                        <div
                          key={idx}
                          className="p-4 rounded-xl border border-slate-200/80 dark:border-slate-800/80 bg-white dark:bg-slate-900/60 text-xs space-y-2 hover:border-purple-400 transition-colors shadow-xs"
                        >
                          <div className="flex items-center justify-between pb-2 border-b border-slate-100 dark:border-slate-800/80">
                            <div className="flex items-center gap-2">
                              <span className="w-5 h-5 rounded-full bg-purple-100 dark:bg-purple-950 text-purple-600 dark:text-purple-400 flex items-center justify-center font-bold text-[11px]">
                                {idx + 1}
                              </span>
                              <span className="font-semibold text-slate-800 dark:text-slate-200">
                                📄 {item.documentName}
                              </span>
                            </div>
                            <Tag color="purple" className="m-0 font-mono text-[11px]">
                              匹配度: {((item.score || 0.85) * 100).toFixed(1)}%
                            </Tag>
                          </div>
                          <div className="bg-slate-50 dark:bg-black/20 p-3 rounded-xl border border-slate-200/60 dark:border-slate-800">
                            <MarkdownRenderer content={item.content} />
                          </div>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              ),
            },
          ]}
        />
      </Drawer>

      {/* 切片查看器 Modal */}
      <Modal
        title={
          <div className="flex items-center justify-between pr-6">
            <div className="flex items-center gap-2">
              <span>🧩 文档切片列表:</span>
              <Tag color="purple" className="max-w-[280px] truncate">{activeDocName}</Tag>
            </div>
            <span className="text-xs font-normal text-slate-500">
              共 <strong className="text-indigo-600 dark:text-indigo-400 font-mono text-sm">{chunkTotal}</strong> 个分块
            </span>
          </div>
        }
        width={900}
        open={chunkModalVisible}
        onCancel={() => setChunkModalVisible(false)}
        footer={
          <div className="flex flex-col sm:flex-row items-center justify-between gap-3 pt-3 border-t border-slate-100 dark:border-slate-800">
            <span className="text-xs text-slate-400">
              当前展示第 {(chunkPageNum - 1) * chunkPageSize + 1} - {Math.min(chunkPageNum * chunkPageSize, chunkTotal)} 项，共 {chunkTotal} 项切片
            </span>
            <Pagination
              size="small"
              current={chunkPageNum}
              pageSize={chunkPageSize}
              total={chunkTotal}
              showSizeChanger
              pageSizeOptions={['10', '20', '50', '100']}
              onChange={handleChunkPageChange}
              showQuickJumper
            />
          </div>
        }
      >
        <div className="max-h-[60vh] overflow-y-auto space-y-3.5 pr-1">
          {chunkLoading ? (
            <div className="text-center py-12">
              <Spin tip="加载切片数据中..." />
            </div>
          ) : chunks.length === 0 ? (
            <Empty description="该文档暂无有效切片段落" className="my-10" />
          ) : (
            chunks.map((c, idx) => (
              <div
                key={c.id}
                className="p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900/60 shadow-xs hover:border-indigo-400 transition-colors space-y-2.5"
              >
                <div className="flex justify-between items-center text-slate-400 font-mono pb-2 border-b border-slate-100 dark:border-slate-800">
                  <div className="flex items-center gap-2">
                    <span className="px-2 py-0.5 rounded-md bg-indigo-50 dark:bg-indigo-950 text-indigo-600 dark:text-indigo-400 font-bold text-xs">
                      # 切片 {(chunkPageNum - 1) * chunkPageSize + idx + 1}
                    </span>
                    <span className="text-[11px] text-slate-400">自然序号: {c.chunkIndex + 1}</span>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="text-xs text-slate-500">预估 Tokens: {c.tokenCount || 0}</span>
                    <button
                      onClick={() => {
                        navigator.clipboard.writeText(c.content);
                        message.success('切片内容已复制');
                      }}
                      className="border-0 bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 text-slate-600 dark:text-slate-300 p-1.5 rounded-lg flex items-center gap-1 text-xs cursor-pointer transition-colors"
                      title="复制切片内容"
                    >
                      <CopyOutlined />
                      <span className="text-[11px]">复制</span>
                    </button>
                  </div>
                </div>
                <div className="pt-1 text-slate-800 dark:text-slate-200">
                  <MarkdownRenderer content={c.content} />
                </div>
              </div>
            ))
          )}
        </div>
      </Modal>
    </div>
  );
};
