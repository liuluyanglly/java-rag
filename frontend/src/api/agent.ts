import request from './request';

export interface AgentItem {
  id: number;
  name: string;
  description: string;
  avatar: string;
  modelName: string;
  systemPrompt: string;
  temperature: number;
  maxTokens: number;
  datasetIds: string; // JSON array string
  tools: string; // JSON array string
  status: string;
  createTime: string;
}

export const getAgentListApi = (params: { pageNum?: number; pageSize?: number; name?: string }) => {
  return request.get<{ records: AgentItem[]; total: number }>('/agent/list', { params });
};

export const getActiveAgentsApi = () => {
  return request.get<AgentItem[]>('/agent/active');
};

export const createAgentApi = (data: Partial<AgentItem>) => {
  return request.post('/agent', data);
};

export const updateAgentApi = (data: Partial<AgentItem>) => {
  return request.put('/agent', data);
};

export const deleteAgentApi = (id: number) => {
  return request.delete(`/agent/${id}`);
};
