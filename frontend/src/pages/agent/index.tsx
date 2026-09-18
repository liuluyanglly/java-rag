import React, { useState, useEffect } from 'react';
import {
  Card,
  Button,
  Tag,
  Modal,
  Form,
  Input,
  InputNumber,
  Select,
  Slider,
  message,
  Popconfirm,
} from 'antd';
import {
  PlusOutlined,
  DeploymentUnitOutlined,
  EditOutlined,
  DeleteOutlined,
  ToolOutlined,
  BookOutlined,
} from '@ant-design/icons';
import {
  getAgentListApi,
  createAgentApi,
  updateAgentApi,
  deleteAgentApi,
  AgentItem,
} from '../../api/agent';
import { getAccessibleDatasetsApi, DatasetItem } from '../../api/dataset';

export const AgentPage: React.FC = () => {
  const [agents, setAgents] = useState<AgentItem[]>([]);
  const [datasets, setDatasets] = useState<DatasetItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingAgent, setEditingAgent] = useState<AgentItem | null>(null);
  const [form] = Form.useForm();

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      setLoading(true);
      const [agentRes, dsRes] = await Promise.all([
        getAgentListApi({ pageNum: 1, pageSize: 50 }),
        getAccessibleDatasetsApi(),
      ]);
      setAgents(agentRes.records);
      setDatasets(dsRes);
    } finally {
      setLoading(false);
    }
  };

  const handleOpenModal = (agent?: AgentItem) => {
    if (agent) {
      setEditingAgent(agent);
      form.setFieldsValue({
        ...agent,
        datasetIds: agent.datasetIds ? JSON.parse(agent.datasetIds) : [],
        tools: agent.tools ? JSON.parse(agent.tools) : [],
      });
    } else {
      setEditingAgent(null);
      form.resetFields();
      form.setFieldsValue({
        modelName: 'deepseek-chat',
        temperature: 0.7,
        maxTokens: 2048,
        systemPrompt: '你是一个专业的企业级 AI 智能助手，请依据上下文知识严谨回答用户问题。',
        tools: ['knowledgeSearchTool'],
      });
    }
    setModalVisible(true);
  };

  const handleSubmit = async (values: any) => {
    try {
      const payload = {
        ...values,
        datasetIds: JSON.stringify(values.datasetIds || []),
        tools: JSON.stringify(values.tools || []),
      };

      if (editingAgent) {
        await updateAgentApi({ ...payload, id: editingAgent.id });
        message.success('智能体更新成功');
      } else {
        await createAgentApi(payload);
        message.success('智能体创建成功');
      }
      setModalVisible(false);
      loadData();
    } catch (e) {}
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteAgentApi(id);
      message.success('删除成功');
      loadData();
    } catch (e) {}
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-xl font-bold text-slate-800 dark:text-slate-100">
            Agent 智能体编排中心
          </h1>
          <p className="text-xs text-slate-400 mt-1">
            定义智能体角色设定、Prompt 提示词模板、模型推理参数以及挂载的 Tools 工具。
          </p>
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => handleOpenModal()}
          className="bg-indigo-600 hover:bg-indigo-500 rounded-lg"
        >
          创建智能体
        </Button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
        {agents.map((a) => (
          <Card
            key={a.id}
            className="border border-slate-200 dark:border-slate-800 hover:shadow-md transition-all rounded-xl"
            actions={[
              <span key="edit" onClick={() => handleOpenModal(a)} className="text-xs text-indigo-600">
                <EditOutlined className="mr-1" /> 配置
              </span>,
              <Popconfirm
                key="del"
                title="确定删除此智能体吗？"
                onConfirm={() => handleDelete(a.id)}
              >
                <span className="text-xs text-red-500 hover:text-red-600">
                  <DeleteOutlined className="mr-1" /> 删除
                </span>
              </Popconfirm>,
            ]}
          >
            <div className="flex items-start gap-3">
              <div className="w-12 h-12 rounded-xl bg-purple-500/10 text-purple-600 dark:text-purple-400 flex items-center justify-center font-bold text-xl flex-shrink-0">
                🤖
              </div>
              <div className="flex-1 overflow-hidden">
                <div className="flex items-center justify-between">
                  <h3 className="text-base font-semibold text-slate-800 dark:text-slate-100 truncate">
                    {a.name}
                  </h3>
                  <Tag color="purple">{a.modelName}</Tag>
                </div>
                <p className="text-xs text-slate-400 line-clamp-2 mt-1">
                  {a.description || '暂无描述'}
                </p>
              </div>
            </div>

            <div className="mt-4 pt-3 border-t border-slate-100 dark:border-slate-800/80 space-y-2 text-xs text-slate-500">
              <div className="flex items-center justify-between">
                <span>多样性 (Temp): {a.temperature}</span>
                <span>Max Tokens: {a.maxTokens}</span>
              </div>
              <div className="flex items-center gap-1 truncate">
                <ToolOutlined className="text-slate-400" />
                <span className="truncate">工具: {a.tools || '[]'}</span>
              </div>
            </div>
          </Card>
        ))}
      </div>

      <Modal
        title={editingAgent ? '编辑智能体' : '新建智能体'}
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        onOk={() => form.submit()}
        width={680}
      >
        <Form form={form} layout="vertical" onFinish={handleSubmit}>
          <Form.Item name="name" label="智能体名称" rules={[{ required: true }]}>
            <Input placeholder="例如: 财务制度问答助手" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="智能体的职责定位说明..." />
          </Form.Item>

          <div className="grid grid-cols-2 gap-4">
            <Form.Item name="modelName" label="基座大模型" rules={[{ required: true }]}>
              <Select
                options={[
                  { label: 'Agnes 3.0 Flash (agnes-3.0-flash - 推荐首选)', value: 'agnes-3.0-flash' },
                  { label: 'Agnes 2.5 Flash (agnes-2.5-flash)', value: 'agnes-2.5-flash' },
                  { label: 'DeepSeek Chat (deepseek-chat)', value: 'deepseek-chat' },
                  { label: 'OpenAI GPT-4o (gpt-4o)', value: 'gpt-4o' },
                  { label: 'OpenAI GPT-4o-mini', value: 'gpt-4o-mini' },
                  { label: '通义千问 (qwen-plus)', value: 'qwen-plus' },
                  { label: 'Ollama 本地模型 (qwen2.5:7b)', value: 'qwen2.5:7b' },
                ]}
              />
            </Form.Item>
            <Form.Item name="maxTokens" label="最大生成 Tokens">
              <InputNumber className="w-full" min={512} max={8192} />
            </Form.Item>
          </div>

          <Form.Item name="temperature" label="随机多样性 (Temperature)">
            <Slider min={0} max={1.5} step={0.1} />
          </Form.Item>

          <Form.Item
            name="systemPrompt"
            label="System Prompt (系统提示词 / 角色人设)"
            rules={[{ required: true }]}
          >
            <Input.TextArea
              rows={4}
              placeholder="设定该智能体的身份角色、专业知识背景、回答风格约束..."
            />
          </Form.Item>

          <Form.Item name="datasetIds" label="关联知识库 (RAG)">
            <Select
              mode="multiple"
              placeholder="选择该智能体允许检索的知识库"
              options={datasets.map((d) => ({ label: d.name, value: d.id }))}
            />
          </Form.Item>

          <Form.Item name="tools" label="挂载工具 / MCP 插件">
            <Select
              mode="multiple"
              placeholder="勾选智能体具备的能力"
              options={[
                { label: '知识库语义检索 (knowledgeSearchTool)', value: 'knowledgeSearchTool' },
                { label: '网络实时搜索 (webSearchTool)', value: 'webSearchTool' },
                { label: 'ERP 业务工单查询 (erpOrderTool)', value: 'erpOrderTool' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};
